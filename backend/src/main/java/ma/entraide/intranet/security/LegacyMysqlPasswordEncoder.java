package ma.entraide.intranet.security;

import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Vérifie les anciens mots de passe de l'application : fonction MySQL PASSWORD(),
 * soit '*' + HEX(SHA1(SHA1(mot de passe))) en majuscules, sans sel.
 * <p>
 * Utilisé uniquement en lecture : à la première connexion réussie, le mot de passe est
 * ré-encodé en bcrypt (voir {@link AuthService}), donc l'utilisateur garde son mot de passe.
 */
public class LegacyMysqlPasswordEncoder implements PasswordEncoder {

    @Override
    public String encode(CharSequence rawPassword) {
        throw new UnsupportedOperationException("Ancien format en lecture seule : utiliser bcrypt");
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (encodedPassword == null || encodedPassword.length() != 41 || encodedPassword.charAt(0) != '*') {
            return false;
        }
        String expected = hash(rawPassword.toString());
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                encodedPassword.toUpperCase().getBytes(StandardCharsets.US_ASCII));
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        return true;
    }

    static String hash(String raw) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] first = sha1.digest(raw.getBytes(StandardCharsets.UTF_8));
            byte[] second = sha1.digest(first);
            return "*" + HexFormat.of().withUpperCase().formatHex(second);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
