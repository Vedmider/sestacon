package io.github.vedmider.sestacon.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** SHA-256 hex content hashing — the identity used for document/chunk version detection. */
public final class ContentHash {

    private ContentHash() {
    }

    public static String of(String content) {
        return hex(digest().digest(content.getBytes(StandardCharsets.UTF_8)));
    }

    /** Section identity = content plus its ACL, so an access change is a change; public sections hash as before. */
    public static String ofSection(String content, java.util.List<String> aclTags) {
        if (aclTags == null || aclTags.isEmpty()) {
            return of(content);
        }
        return of(content + "\u0000acl:" + String.join(",", new java.util.TreeSet<>(aclTags)));
    }

    /** Document hash = hash of its ordered chunk hashes, so any chunk change changes the doc hash. */
    public static String ofChunks(java.util.List<String> orderedChunkHashes) {
        return of(String.join("\n", orderedChunkHashes));
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
