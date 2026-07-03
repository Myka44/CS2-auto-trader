package org.example.integration.dmarket;

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;

import java.nio.charset.StandardCharsets;

/** Ed25519 request signing for the DMarket Trading API. Unchanged from the original Utils.java. */
public final class DMarketSigner {

    private DMarketSigner() {}

    public static String signMessage(String message, String secretKeyHex) {
        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
        byte[] secretKeyBytes = hexStringToByteArray(secretKeyHex);

        Ed25519PrivateKeyParameters privateKeyParameters = new Ed25519PrivateKeyParameters(secretKeyBytes, 0);
        Ed25519Signer signer = new Ed25519Signer();
        signer.init(true, privateKeyParameters);
        signer.update(messageBytes, 0, messageBytes.length);

        byte[] signature = signer.generateSignature();
        return byteArrayToHex(signature);
    }

    public static byte[] hexStringToByteArray(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    public static String byteArrayToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xFF & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
