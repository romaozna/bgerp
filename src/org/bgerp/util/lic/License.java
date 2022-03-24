package org.bgerp.util.lic;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

import ru.bgcrm.util.ParameterMap;
import ru.bgcrm.util.Preferences;
import ru.bgcrm.util.Utils;

/**
 * License for plugins.
 *
 * @author Shamil Vakhitov
 */
public class License {
    /**
     * Accepted signature.
     */
    private static final Sign SIGN = new Sign("Team <team@bgerp.org>", "AAAAB3NzaC1yc2EAAAADAQABAAABAQDE4LeZHn/rW/J5fX52ozR2B+wwxEqfE9lhkZDmG3wCCtGNXzxFpQXVRROHi0FwSZAnQXLvTwMH1Lw54SBxPbEk3f35B3ULorIzibMwokzdD6daJdmI9nq4fm7FcpnM8Wv81RvRbKjBFQz1waJLiALpTxBSOrgbFFM6jilgv9fSEJNsz2c/sh/TlMxa5XlHhwutdp6qip2QyTngD8oq1ZNtHqzx3kI/tj2L+fRZEhWZD2Fj9oWKs9uiS+G4Gzsty2bA6hHYMyDdzFKUvN3I9Lj9NF2ZeLalsen5zaoHh5kzPyzCymZfVupu6M1DQqBtE5rQgY+JWXDegBYzRawiwzpx");

    public static final String FILE_NAME = "lic.data";

    private static final String KEY_LIC = "lic.";
    public static final String KEY_LIC_ID = KEY_LIC + "id";
    public static final String KEY_LIC_EMAIL = KEY_LIC + "email";
    public static final String KEY_LIC_LIMIT = KEY_LIC + "limit";
    public static final String KEY_LIC_DATE_TO = KEY_LIC + "date.to";
    public static final String KEY_LIC_PLUGIN = KEY_LIC + "plugin.";
    private static final int KEY_LIC_PLUGIN_LENGTH = KEY_LIC_PLUGIN.length();
    public static final String KEY_LIC_SIGN = KEY_LIC + "sign";

    private final String data;
    private final ParameterMap config;
    private final byte[] digest;
    private final String error;
    private final Set<String> plugins;

    public License(String data) {
        this.data = data;
        this.config = new Preferences(data);
        this.digest = digest();
        this.error = check();
        this.plugins = plugins();
    }

    /**
     * License content.
     * @return
     */
    public String getData() {
        return data;
    }

    /**
     * Digest for all the license's lines before lic.sign.signature.
     * @return
     */
    public byte[] getDigest() {
        return digest;
    }

    /**
     * License check result.
     * @return {@code null} on correct result, or error text.
     */
    public String getError() {
        return error;
    }

    /**
     * Plugin IDs.
     * @return
     */
    public Set<String> getPlugins() {
        return plugins;
    }

    /**
     * License content with signature on the end.
     * @param keyFilePath file of Java resource path to SSH private key file.
     * @param keyFilePswd password to SSH private key file, {@code null} - no password is used.
     * @return UTF-8 encoded signed license.
     */
    public byte[] sign(String keyFilePath, String keyFilePswd) throws Exception {
        var sign = new Sign("key.id", new String(ru.bgcrm.util.io.IOUtils.read(keyFilePath), StandardCharsets.UTF_8), keyFilePswd);

        var data = new StringBuilder(this.data);
        data.append(KEY_LIC_SIGN + "=")
            .append(sign.signatureGenerate(getDigest()))
            .append("\n");

        return data.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] digest() {
        var buffer = new StringBuilder(1000);

        try (var scanner = new Scanner(data)) {
            while (scanner.hasNextLine()) {
                var line = scanner.nextLine();
                if (line.startsWith(KEY_LIC_SIGN))
                    break;
                buffer.append(line);
            }
        }

        try {
            var digest = MessageDigest.getInstance("SHA-512");
            return digest.digest(buffer.toString().getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Digest exception", e);
        }
    }

    private String check() {
        var signature = config.get(KEY_LIC_SIGN);

        if (Utils.isEmptyString(signature))
            return "Signature is undefined";

        if (!SIGN.signatureVerify(digest, signature))
            return "Signature is not correct";

        // TODO: Check period. Enabled plugins.

        return null;
    }

    private Set<String> plugins() {
        var result = new HashSet<String>();
        for (var me : config.entrySet()) {
            var key = me.getKey();
            if (!key.startsWith(KEY_LIC_PLUGIN) || !Utils.parseBoolean(me.getValue(), false))
                continue;

            result.add(key.substring(KEY_LIC_PLUGIN_LENGTH));
        }

        return Collections.unmodifiableSet(result);
    }
}
