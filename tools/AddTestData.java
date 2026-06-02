import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
public class AddTestData {
    static final String DEVICE_ID = "2ae571a095343c67";
    static final String FIREBASE_URL = "https://self-note-636a2-default-rtdb.asia-southeast1.firebasedatabase.app";
    static final String BASE_PATH = "users/" + DEVICE_ID + "/notes/" + DEVICE_ID;
    static SecretKeySpec deriveKey(String deviceId) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(deviceId.getBytes("UTF-8"));
        byte[] keyBytes = new byte[16];
        System.arraycopy(hash, 0, keyBytes, 0, 16);
        return new SecretKeySpec(keyBytes, "AES");
    }
    static String encrypt(String plainText, String deviceId) throws Exception {
        if (plainText.isEmpty()) return plainText;
        SecretKeySpec key = deriveKey(deviceId);
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        byte[] cipherBytes = cipher.doFinal(plainText.getBytes("UTF-8"));
        byte[] combined = new byte[iv.length + cipherBytes.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(cipherBytes, 0, combined, iv.length, cipherBytes.length);
        return "G:" + Base64.getEncoder().encodeToString(combined);
    }
    static void pushToFirebase(String noteId, String json) throws Exception {
        String urlStr = FIREBASE_URL + "/" + BASE_PATH + "/" + noteId + ".json";
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("PUT");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        OutputStream os = conn.getOutputStream();
        os.write(json.getBytes("UTF-8"));
        os.flush();
        os.close();
        int code = conn.getResponseCode();
        BufferedReader br = new BufferedReader(new InputStreamReader(
            code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) response.append(line);
        br.close();
        System.out.println("  HTTP " + code + ": " + response.toString().substring(0, Math.min(80, response.length())));
    }
    public static void main(String[] args) throws Exception {
        String[][] notes = {
            {"Smart Chips: Phone Test", "Call John at 9876543210 for the meeting. His office number is +91-11-2345-6789. Also reach out to Sarah at (555) 123-4567."},
            {"Smart Chips: Email Test", "Send the report to john.doe@example.com by Friday. CC marketing@company.org and dev-team@startup.io for review."},
            {"Smart Chips: URL Test", "Check the docs at https://developer.android.com/jetpack/compose and the repo at https://github.com/example/project."},
            {"Smart Chips: Address Test", "Meeting location: 221B Baker Street, London. Backup venue: 1600 Amphitheatre Parkway, Mountain View, CA 94043."},
            {"Smart Chips: Mixed Entities", "Call Dr. Sharma at 9988776655 tomorrow at 5 PM. Email confirmation to appointments@clinic.com. Clinic address: 42 M.G. Road, Bangalore 560001. Website: https://drsharma-clinic.com/book"}
        };
        System.out.println("Adding test data to Firebase for device: " + DEVICE_ID);
        for (String[] note : notes) {
            String noteId = UUID.randomUUID().toString();
            long timestamp = System.currentTimeMillis();
            String encTitle = encrypt(note[0], DEVICE_ID);
            String encDesc = encrypt(note[1], DEVICE_ID);
            String json = String.format(
                "{\"title\":\"%s\",\"description\":\"%s\",\"id\":\"%s\",\"mymobiledeviceid\":\"%s\",\"timestamp\":%d,\"updatedAt\":%d}",
                encTitle.replace("\"", "\\\""), encDesc.replace("\"", "\\\""),
                noteId, DEVICE_ID, timestamp, timestamp);
            System.out.println("Pushing: " + note[0]);
            pushToFirebase(noteId, json);
        }
        System.out.println("Done! 5 test notes added.");
    }
}
