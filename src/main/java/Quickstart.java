import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Base64;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.Draft;
import com.google.api.services.gmail.model.Message;

import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.io.*;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.lang.System.out;
import static javax.mail.Message.RecipientType;

public class Quickstart {

    /**
     * Application name.
     */
    private static final String APPLICATION_NAME = "Work Journal Sender";

    /**
     * Directory to store user credentials for this application.
     */
    private static final File DATA_STORE_DIR = new File(System.getProperty("user.home"), ".credentials/gmail-java-quickstart");

    /**
     * Global instance of the JSON factory.
     */
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    /**
     * Global instance of the scopes required by this quickstart.
     * <p>
     * If modifying these scopes, delete your previously saved credentials
     * at ~/.credentials/gmail-java-quickstart
     */
    private static final List<String> SCOPES = Arrays.asList(GmailScopes.GMAIL_LABELS, GmailScopes.GMAIL_COMPOSE);

    /**
     * Email address. {@code "me"} is a special value indicating the authenticated user.
     */
    private static final String ME = "me";

    private static final String SENDER = "tony.robalik@gmail.com";
    private static final String RECIPIENT = "chesscom-mobile-developers@googlegroups.com";
    private static final String SUBJECT_BASE = "[CC-Report] %s Work Journal"; // %s will be replaced with date in DD/MM format

    /**
     * For use when constructing email from work journal file.
     */
    private static final AtomicBoolean FILTER = new AtomicBoolean(true);

    /**
     * Global instance of the {@link FileDataStoreFactory}.
     */
    private static FileDataStoreFactory DATA_STORE_FACTORY;

    /**
     * Global instance of the HTTP transport.
     */
    private static HttpTransport HTTP_TRANSPORT;

    static {
        try {
            HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            DATA_STORE_FACTORY = new FileDataStoreFactory(DATA_STORE_DIR);
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }

    public static void main(String[] args) throws Exception {
        // Build a new authorized API client service.
        Gmail service = getGmailService();

        // Print the labels in the user's account.
//        ListLabelsResponse listResponse = service.users().labels().list(ME).execute();
//        List<Label> labels = listResponse.getLabels();
//        if (labels.size() == 0) {
//            out.println("No labels found.");
//        } else {
//            out.println("Labels:");
//            labels.stream()
//                    .sorted((l1, l2) -> l1.getName().compareTo(l2.getName()))
//                    .forEach(label -> out.printf("- %s\n", label.getName()));
//        }

        // Send test message OR create draft
        File workJournal = workJournalFile();
        out.println("Work Journal file=" + workJournal.toString());
        if (workJournal.exists()) {
            MimeMessage emailContent = createEmail(RECIPIENT, SENDER, subject(), workJournal);
//        sendMessage(service, ME, emailContent);
            createDraft(service, ME, emailContent);
        }
    }

    private static File workJournalFile() {
        String filename = "work_journal_";
        String date = new SimpleDateFormat("yyyy_MM_dd").format(new Date());
        filename = filename + date + ".txt";

        File home = new File(System.getProperty("user.home"), "workspace/chess/work_journal");
        return new File(home, filename);
    }

    /**
     * Build and return an authorized Gmail client service.
     *
     * @return an authorized Gmail client service
     * @throws IOException
     */
    public static Gmail getGmailService() throws IOException {
        Credential credential = authorize();
        return new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    /**
     * Create a MimeMessage using the parameters provided.
     *
     * @param to      email address of the receiver
     * @param from    email address of the sender, the mailbox account
     * @param subject subject of the email
     * @param body    file with body text of the email
     * @return the MimeMessage to be used to send email
     * @throws MessagingException
     */
    public static MimeMessage createEmail(String to, String from, String subject, File body) throws MessagingException, IOException {
        String bodyText = Files.readAllLines(body.toPath())
                .stream()
                .peek(line -> {
                    if (line.startsWith("===")) {
                        // filter everything after '==='
                        FILTER.set(false);
                    }
                })
                .filter(ignored -> FILTER.get())
                .collect(Collectors.joining("\n"));

        bodyText = embolden(messageDate()) + "\n\n" + bodyText;

        return createEmail(to, from, subject, bodyText);
    }

    private static String messageDate() {
        // TODO replace with Java 8 JSR 310 stuff (see: http://docs.oracle.com/javase/tutorial/datetime/iso/format.html)
        return new SimpleDateFormat("E, M/d/yyyy").format(new Date());
    }

    private static String embolden(String text) {
        return "<strong>" + text + "</strong>";
    }

    /**
     * Create a MimeMessage using the parameters provided.
     *
     * @param to       email address of the receiver
     * @param from     email address of the sender, the mailbox account
     * @param subject  subject of the email
     * @param bodyText body text of the email
     * @return the MimeMessage to be used to send email
     * @throws MessagingException
     */
    public static MimeMessage createEmail(String to, String from, String subject, String bodyText) throws MessagingException {
        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);

        MimeMessage email = new MimeMessage(session);
        email.setFrom(new InternetAddress(from));
        email.addRecipient(RecipientType.TO, new InternetAddress(to));
        email.setSubject(subject);
//        email.setText(bodyText);

        // HTML content
        Multipart multipart = new MimeMultipart("alternative");
        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText(bodyText, "utf-8");
        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(htmlFromText(bodyText), "text/html; charset=utf-8");
        multipart.addBodyPart(textPart);
        multipart.addBodyPart(htmlPart);
        email.setContent(multipart);

        return email;
    }

    private static String htmlFromText(String text) {
        text = htmlStrong(text);
        text = htmlUnderline(text);
        text = htmlJiraLinks(text);
        text = htmlPrLinks(text);
        text = htmlStrikethrough(text);

        return "<html>" + text.replaceAll("\n", "<br />") + "</html>";
    }

    private static final String WHITESPACE = "\\s+";

    private static String htmlStrong(String text) {
        Pattern p = Pattern.compile(WHITESPACE + "\\*[^*]+\\*"); // *This is bold* (must start with whitespace character)
        Matcher m = p.matcher(text);

        while (m.find()) {
            String group = m.group();
            text = text.replace(group, "<strong>" + group.replaceAll("\\*", "") + "</strong>");
        }
        return text;
    }

    private static String htmlUnderline(String text) {
        Pattern p = Pattern.compile(WHITESPACE + "_[^_]+_"); // _This is underlined_
        Matcher m = p.matcher(text);

        while (m.find()) {
            String group = m.group();
            text = text.replace(group, "<u>" + group.replaceAll("_", "") + "</u>");
        }
        return text;
    }

    private static String htmlJiraLinks(String text) {
        Pattern p = Pattern.compile("(AN|ACK|CV)-\\d+"); // AN-123, ACK-123, CV-12345

        // TODO this will still break if there is more than one identical match on the same line.
        return Arrays.stream(text.split("\n"))
                .map(line -> {
                    Matcher m = p.matcher(line);
                    while (m.find()) {
                        String group = m.group();
                        line = line.replace(group, jiraLink(group));
                    }
                    return line;
                })
                .collect(Collectors.joining("\n"));
    }

    private static String jiraLink(String ticket) {
        // e.g., https://chesscom.atlassian.net/browse/AN-1176
        return String.format(Locale.US, "<a href=\"https://chesscom.atlassian.net/browse/%s\">%s</a>", ticket, ticket);
    }

    private static String htmlPrLinks(String text) {
        Pattern p = Pattern.compile("PR ?#\\d+"); // `PR #123` or `PR#123`
        Matcher m = p.matcher(text);

        while (m.find()) {
            String group = m.group();
            text = text.replace(group, githubLinks(group));
        }
        return text;
    }

    private static String githubLinks(String pr) {
        String prNum = pr.substring(pr.indexOf("#") + 1); // if pr = PR #123, then -> 123
        // https://github.com/ChessCom/android/pull/814
        return String.format(Locale.US, "<a href=\"https://github.com/ChessCom/android/pull/%s\">%s</a>", prNum, pr);
    }

    private static String htmlStrikethrough(String text) {
        Pattern p = Pattern.compile(WHITESPACE + "-[^-]+-");
        Matcher m = p.matcher(text);

        while (m.find()) {
            String group = m.group();
            text = text.replace(group, "<strike>" + group.replaceAll("-", "") + "</strike>");
        }
        return text;
    }

    private static String subject() {
        // TODO replace with Java 8 JSR 310 stuff (see: http://docs.oracle.com/javase/tutorial/datetime/iso/format.html)
        String date = new SimpleDateFormat("M/d").format(new Date());
//        if (date.startsWith("0")) {
//            date = date.substring(1);
//        }
        return String.format(SUBJECT_BASE, date);
    }

    /**
     * Create draft email.
     *
     * @param service      an authorized Gmail API instance
     * @param userId       user's email address. The special value "me"
     *                     can be used to indicate the authenticated user
     * @param emailContent the MimeMessage used as email within the draft
     * @return the created draft
     * @throws MessagingException
     * @throws IOException
     */
    public static Draft createDraft(Gmail service, String userId, MimeMessage emailContent) throws MessagingException, IOException {
        Message message = createMessageWithEmail(emailContent);
        Draft draft = new Draft();
        draft.setMessage(message);
        draft = service.users().drafts().create(userId, draft)
                .execute();

        out.println("Draft id: " + draft.getId());
        out.println(draft.toPrettyString());
        return draft;
    }

    /**
     * Creates an authorized Credential object.
     *
     * @return an authorized Credential object.
     * @throws IOException
     */
    public static Credential authorize() throws IOException {
        // Load client secrets.
        InputStream in = Quickstart.class.getResourceAsStream("/client_secret.json");
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow =
                new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                        .setDataStoreFactory(DATA_STORE_FACTORY)
                        .setAccessType("offline")
                        .build();
        Credential credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver())
                .authorize("user");
        out.println("Credentials saved to " + DATA_STORE_DIR.getAbsolutePath());
        return credential;
    }

    /**
     * Create a message from an email.
     *
     * @param emailContent Email to be set to raw of message
     * @return a message containing a base64url encoded email
     * @throws IOException
     * @throws MessagingException
     */
    public static Message createMessageWithEmail(MimeMessage emailContent) throws MessagingException, IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        emailContent.writeTo(buffer);
        byte[] bytes = buffer.toByteArray();
        String encodedEmail = Base64.encodeBase64URLSafeString(bytes);
        Message message = new Message();
        message.setRaw(encodedEmail);

        return message;
    }

    /**
     * Send an email from the user's mailbox to its recipient.
     *
     * @param service      Authorized Gmail API instance.
     * @param userId       User's email address. The special value "me"
     *                     can be used to indicate the authenticated user.
     * @param emailContent Email to be sent.
     * @return The sent message
     * @throws MessagingException
     * @throws IOException
     */
    public static Message sendMessage(Gmail service, String userId, MimeMessage emailContent) throws MessagingException, IOException {
        Message message = createMessageWithEmail(emailContent);
        message = service.users().messages().send(userId, message)
                .execute();

        out.println("Message id: " + message.getId());
        out.println(message.toPrettyString());
        return message;
    }

}
