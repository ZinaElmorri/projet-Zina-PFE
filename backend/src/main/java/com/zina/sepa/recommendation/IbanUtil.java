package com.zina.sepa.recommendation;

/** Masquage d'IBAN pour l'affichage et les messages (FR76****1234). */
public final class IbanUtil {

    private IbanUtil() {
    }

    public static String masquer(String iban) {
        if (iban == null) {
            return "";
        }
        String s = iban.replaceAll("\\s", "");
        if (s.length() <= 10) {
            return s;
        }
        return s.substring(0, 6) + "****" + s.substring(s.length() - 4);
    }
}
