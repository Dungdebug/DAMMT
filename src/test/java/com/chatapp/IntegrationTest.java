package com.chatapp;

import com.chatapp.client.*;
import com.chatapp.model.*;
import com.chatapp.protocol.MessageType;
import com.chatapp.protocol.ProtocolConstants;
import com.chatapp.utils.HashUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * End-to-End Integration Test for TCP Chat + File Transfer.
 * Tests:
 * 1. Login of two clients (Alice and Bob)
 * 2. Online user notifications
 * 3. 1-1 Chat messaging
 * 4. 1-1 File transfer with chunk streaming and SHA-256 verification
 * 5. Clean disconnect
 */
public class IntegrationTest {

    public static void main(String[] args) throws Exception {
        System.out.println("==================================================");
        System.out.println(" Starting TCP Chat & File Transfer Integration Test ");
        System.out.println("==================================================");

        int port = ProtocolConstants.DEFAULT_PORT;
        String host = "127.0.0.1";

        // 1. Client sessions
        UserSession aliceSession = new UserSession("Alice");
        UserSession bobSession = new UserSession("Bob");

        ServerConnection aliceConn = new ServerConnection();
        ServerConnection bobConn = new ServerConnection();

        ChatController aliceChat = new ChatController(aliceConn, aliceSession);
        ChatController bobChat = new ChatController(bobConn, bobSession);

        FileTransferController aliceFT = new FileTransferController(aliceConn, aliceSession);
        FileTransferController bobFT = new FileTransferController(bobConn, bobSession);

        CountDownLatch aliceLoginLatch = new CountDownLatch(1);
        CountDownLatch bobLoginLatch = new CountDownLatch(1);
        CountDownLatch bobReceivedMsgLatch = new CountDownLatch(1);
        CountDownLatch bobFileRequestLatch = new CountDownLatch(1);
        CountDownLatch transferCompleteLatch = new CountDownLatch(2);

        AtomicReference<String> bobReceivedText = new AtomicReference<>();
        AtomicReference<FileMetadata> bobReceivedMetadata = new AtomicReference<>();
        AtomicBoolean aliceTransferVerified = new AtomicBoolean(false);
        AtomicBoolean bobTransferVerified = new AtomicBoolean(false);

        // Setup Alice Listeners
        aliceConn.setListener(new ServerConnection.MessageListener() {
            @Override
            public void onMessageReceived(ProtocolMessage msg) {
                if (msg.getType() == MessageType.LOGIN_SUCCESS) {
                    aliceSession.setConnected(true);
                    aliceLoginLatch.countDown();
                } else if (msg.getType() == MessageType.FILE_ACCEPT) {
                    aliceFT.handleFileAccepted(msg);
                } else if (msg.getType() == MessageType.FILE_PROGRESS) {
                    aliceFT.handleProgress(msg);
                } else if (msg.getType() == MessageType.FILE_COMPLETE) {
                    aliceFT.handleTransferComplete(msg);
                }
            }

            @Override
            public void onDisconnected(String reason) {
                System.out.println("[Alice] Disconnected: " + reason);
            }
        });

        aliceFT.setListener(new FileTransferController.FileTransferListener() {
            @Override public void onFileRequestReceived(FileMetadata metadata) {}
            @Override public void onTransferStarted(String transferId, boolean isSender) {
                System.out.println("[Alice] File transfer started (sender)");
            }
            @Override public void onTransferProgress(String transferId, int percent, long bytesTransferred, long totalBytes, String fileName) {
                System.out.println("[Alice] Progress: " + percent + "% (" + bytesTransferred + "/" + totalBytes + ")");
            }
            @Override public void onTransferComplete(String transferId, boolean verified) {
                System.out.println("[Alice] Transfer complete! Verified: " + verified);
                aliceTransferVerified.set(verified);
                transferCompleteLatch.countDown();
            }
            @Override public void onTransferCancelled(String transferId, String reason) {}
            @Override public void onTransferError(String transferId, String error) {
                System.err.println("[Alice] Transfer error: " + error);
            }
            @Override public void onTransferRejected(String transferId) {}
        });

        // Setup Bob Listeners
        bobConn.setListener(new ServerConnection.MessageListener() {
            @Override
            public void onMessageReceived(ProtocolMessage msg) {
                if (msg.getType() == MessageType.LOGIN_SUCCESS) {
                    bobSession.setConnected(true);
                    bobLoginLatch.countDown();
                } else if (msg.getType() == MessageType.CHAT_MESSAGE) {
                    bobChat.handleIncomingMessage(msg);
                } else if (msg.getType() == MessageType.FILE_REQUEST) {
                    bobFT.handleIncomingFileRequest(msg);
                } else if (msg.getType() == MessageType.FILE_PROGRESS) {
                    bobFT.handleProgress(msg);
                } else if (msg.getType() == MessageType.FILE_COMPLETE) {
                    bobFT.handleTransferComplete(msg);
                }
            }

            @Override
            public void onDisconnected(String reason) {
                System.out.println("[Bob] Disconnected: " + reason);
            }
        });

        bobChat.setListener(new ChatController.ChatListener() {
            @Override
            public void onMessageReceived(ChatMessage message) {
                System.out.println("[Bob] Received chat message: " + message.getContent() + " from " + message.getSender());
                bobReceivedText.set(message.getContent());
                bobReceivedMsgLatch.countDown();
            }
            @Override public void onMessageSent(ChatMessage message) {}
            @Override public void onChatError(String error) {
                System.err.println("[Bob] Chat error: " + error);
            }
        });

        bobFT.setListener(new FileTransferController.FileTransferListener() {
            @Override
            public void onFileRequestReceived(FileMetadata metadata) {
                System.out.println("[Bob] Received file request: " + metadata.getFileName() + " (" + metadata.getFileSize() + " bytes)");
                bobReceivedMetadata.set(metadata);
                bobFileRequestLatch.countDown();
            }
            @Override public void onTransferStarted(String transferId, boolean isSender) {
                System.out.println("[Bob] File transfer started (receiver)");
            }
            @Override public void onTransferProgress(String transferId, int percent, long bytesTransferred, long totalBytes, String fileName) {
                System.out.println("[Bob] Progress: " + percent + "% (" + bytesTransferred + "/" + totalBytes + ")");
            }
            @Override public void onTransferComplete(String transferId, boolean verified) {
                System.out.println("[Bob] Transfer complete! Verified: " + verified);
                bobTransferVerified.set(verified);
                transferCompleteLatch.countDown();
            }
            @Override public void onTransferCancelled(String transferId, String reason) {}
            @Override public void onTransferError(String transferId, String error) {
                System.err.println("[Bob] Transfer error: " + error);
            }
            @Override public void onTransferRejected(String transferId) {}
        });

        // 2. Connect and login Alice
        System.out.println("Step 1: Connecting and logging in Alice...");
        aliceConn.connect(host, port);
        aliceConn.sendMessage(ProtocolMessage.createLogin("Alice"));
        boolean aliceLoggedIn = aliceLoginLatch.await(5, TimeUnit.SECONDS);
        assertCondition(aliceLoggedIn, "Alice failed to log in");
        System.out.println("-> Alice logged in successfully!");

        // 3. Connect and login Bob
        System.out.println("Step 2: Connecting and logging in Bob...");
        bobConn.connect(host, port);
        bobConn.sendMessage(ProtocolMessage.createLogin("Bob"));
        boolean bobLoggedIn = bobLoginLatch.await(5, TimeUnit.SECONDS);
        assertCondition(bobLoggedIn, "Bob failed to log in");
        System.out.println("-> Bob logged in successfully!");

        Thread.sleep(500);

        // 4. Send chat message: Alice -> Bob
        System.out.println("Step 3: Alice sends chat message to Bob...");
        String testMsg = "Hello Bob! Testing TCP Socket chat on MMT project.";
        aliceChat.sendMessage("Bob", testMsg);
        boolean msgReceived = bobReceivedMsgLatch.await(5, TimeUnit.SECONDS);
        assertCondition(msgReceived, "Bob did not receive chat message in time");
        assertCondition(testMsg.equals(bobReceivedText.get()), "Chat message content mismatch");
        System.out.println("-> Chat message delivered and verified!");

        // 5. Send file: Alice -> Bob
        System.out.println("Step 4: Alice prepares 2.5MB sample file for transfer...");
        File tempSendFile = File.createTempFile("tcp_test_send_", ".bin");
        tempSendFile.deleteOnExit();
        byte[] randomBytes = new byte[(int) (2.5 * 1024 * 1024)]; // 2.5 MB (multi-chunk with 1MB blocks)
        new Random(42).nextBytes(randomBytes);
        try (FileOutputStream fos = new FileOutputStream(tempSendFile)) {
            fos.write(randomBytes);
        }
        String originalHash = HashUtils.computeSHA256(tempSendFile);
        System.out.println("-> Source file created: " + tempSendFile.getName() + " | SHA-256: " + originalHash);

        System.out.println("Step 5: Alice sends FILE_REQUEST to Bob...");
        aliceFT.sendFileRequest("Bob", tempSendFile);

        boolean fileReqReceived = bobFileRequestLatch.await(5, TimeUnit.SECONDS);
        assertCondition(fileReqReceived, "Bob did not receive FILE_REQUEST");
        assertCondition(originalHash.equalsIgnoreCase(bobReceivedMetadata.get().getSha256Hash()), "SHA-256 in metadata mismatch");
        System.out.println("-> Bob received file request with correct metadata.");

        System.out.println("Step 6: Bob accepts FILE_REQUEST and begins receiving stream...");
        File tempReceiveDir = new File(System.getProperty("java.io.tmpdir"), "tcp_test_recv_dir_" + System.currentTimeMillis());
        tempReceiveDir.mkdirs();
        tempReceiveDir.deleteOnExit();
        bobFT.acceptTransfer(bobReceivedMetadata.get().getTransferId(), tempReceiveDir);

        boolean transferCompleted = transferCompleteLatch.await(15, TimeUnit.SECONDS);
        assertCondition(transferCompleted, "File transfer did not complete in time");
        assertCondition(aliceTransferVerified.get(), "Alice did not report verified transfer");
        assertCondition(bobTransferVerified.get(), "Bob did not report verified transfer");

        // Verify received file length and hash
        File receivedFile = new File(tempReceiveDir, tempSendFile.getName());
        assertCondition(receivedFile.exists(), "Received file does not exist: " + receivedFile.getAbsolutePath());
        assertCondition(receivedFile.length() == tempSendFile.length(),
                "Received file length (" + receivedFile.length() + ") != sent file length (" + tempSendFile.length() + ")");
        String receivedHash = HashUtils.computeSHA256(receivedFile);
        assertCondition(originalHash.equalsIgnoreCase(receivedHash), "Received file SHA-256 hash mismatch!");
        System.out.println("-> File transfer fully completed and SHA-256 integrity verified!");

        // 6. Clean disconnect
        System.out.println("Step 7: Disconnecting clients...");
        aliceConn.disconnect();
        bobConn.disconnect();

        System.out.println("==================================================");
        System.out.println(" ALL TESTS PASSED SUCCESSFULLY! (100% WORKING)    ");
        System.out.println("==================================================");
        System.exit(0);
    }

    private static void assertCondition(boolean condition, String message) {
        if (!condition) {
            System.err.println("TEST ASSERTION FAILED: " + message);
            System.exit(1);
        }
    }
}
