package com.kaidad.gmailreader;

import com.ricebridge.csvman.CsvManager;
import com.sun.mail.imap.IMAPSSLStore;

import javax.mail.Address;
import javax.mail.FetchProfile;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.URLName;

import java.io.IOException;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import static java.util.Collections.sort;

public class GmailReader {

    private Session session;
    private Properties properties;

    public GmailReader() {
        Security.addProvider(new com.sun.net.ssl.internal.ssl.Provider());
        properties = new Properties();
        try {
            properties.load(getClass().getResourceAsStream("/config.properties"));
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not load config.properties - call Dave and give him some shit!");
        }

        session = Session.getInstance(properties, null);
    }

    public List<String[]> extractFromAddresses(final String username, final String password) {
        return extractFromAddresses(username, password, null, null);
    }

    public List<String[]> extractFromAddresses(final String username, final String password, Integer start,
                                               Integer stop) {

        Store store = null;
        Folder folder = null;
        try {
            store = getStore(username, password);

            System.out.println("Fetching messages from INBOX - this can take a LONG time - be patient...");
            folder = store.getFolder("INBOX");
            folder.open(Folder.READ_ONLY);
            Message[] messages;
            int messageCount = folder.getMessageCount();
            if (start != null && stop != null) {
                if (messageCount < (stop - start + 1)) {
                    stop = messageCount;
                }
                messages = folder.getMessages(start, stop);
            } else {
                messages = folder.getMessages();
            }
            FetchProfile fp = new FetchProfile();
            folder.fetch(messages, fp);
            if (messages == null || messages.length == 0) {
                System.out.println("INBOX has NO messages - did you use the correct account?");
                return Collections.emptyList();
            } else {
                System.out.println("INBOX has " + messages.length + " messages - extracting \"from\" addresses");
                return extractFromAddresses(messages);
            }
        } catch (MessagingException ex) {
            throw new IllegalStateException("Failed to read INBOX - send this to Dave: " + ex.getMessage());
        } finally {
            close(folder, store);
        }
    }

    private Store getStore(final String username, final String password) {
        System.out.println("Connecting to mail account (" + username + ")");
        int port = Integer.parseInt(properties.getProperty("port"));
        URLName urlName =
                new URLName(properties.getProperty("protocol"), properties.getProperty("host"), port, null, username,
                        password);
        IMAPSSLStore store = new IMAPSSLStore(session, urlName);
        try {
            store.connect(properties.getProperty("host"), username, password);
        } catch (MessagingException e) {
            System.out.println("Unable to connect to account (" + username + ") - " + e.getMessage());
        }
        return store;
    }

    private void close(Folder folder, Store store) {
        try {
            if (folder != null && folder.isOpen()) {
                folder.close(true);
            }
            if (store != null) {
                store.close();
            }
        } catch (MessagingException ex) {
            ex.printStackTrace();
        }
    }

    private List<String[]> extractFromAddresses(Message[] messages) {
        if (messages == null || messages.length == 0) {
            System.out.println("No messages found to transform");
            return null;
        }

        Set<String> uniqueEmailAddresses = new HashSet<String>(2048);
        for (final Message message : messages) {
            System.out.print('.');
            try {
                Address[] addressList = message.getFrom();
                for (final Address anAddressList : addressList) {
                    uniqueEmailAddresses.add(anAddressList.toString());
                }
            } catch (MessagingException ex) {
                System.out.println("Messages transformation failed - send this to Dave:" + ex);
            }

        }
        System.out.println("done");
        return uniqueEmailsToList(uniqueEmailAddresses);
    }

    private List<String[]> uniqueEmailsToList(final Set<String> uniqueEmailAddresses) {
        List<String[]> arrayList = new ArrayList<String[]>();
        for (String address : uniqueEmailAddresses) {
            arrayList.add(new String[]{address});
        }
        sort(arrayList, new Comparator<String[]>() {
            @Override
            public int compare(final String[] o1, final String[] o2) {
                return arrayToString(o1).compareTo(arrayToString(o2));
            }

            private String arrayToString(final String[] o1) {
                StringBuilder sb = new StringBuilder();
                for (String s : o1) {
                    sb.append(s);
                }
                return sb.toString();
            }
        });
        return arrayList;
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Usage: GmailReader username password outputfile");
            System.exit(1);
        }
        GmailReader reader = new GmailReader();
        List<String[]> fromAddresses = reader.extractFromAddresses(args[0], args[1]);
        System.out
                .println("A total of " + fromAddresses.size() + " unique email addresses found, writing to " + args[2]);
        CsvManager csvManager = new CsvManager();
        csvManager.save(args[2], fromAddresses);
    }

}