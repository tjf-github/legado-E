import io.legado.app.help.book.diagnostics.DiagnosticRedactor;
class P1RedactionProbe {
    public static void main(String[] args) {
        String[] samples = {
            "https://example.invalid/page?chapter=SYNTHETIC_PRIVATE_VALUE#SYNTHETIC_FRAGMENT",
            "Cookie: SYNTHETIC_COOKIE_VALUE",
            "Authorization: SYNTHETIC_AUTH_VALUE"
        };
        int leaks = 0;
        for (String input : samples) {
            String actual = DiagnosticRedactor.INSTANCE.sanitizeMessage(input);
            boolean leaked = actual.contains("SYNTHETIC_");
            if (leaked) leaks++;
            System.out.println("synthetic_input=" + input);
            System.out.println("actual=" + actual);
            System.out.println("sensitive_marker_retained=" + leaked);
        }
        System.out.println("leaking_cases=" + leaks + "/" + samples.length);
        if (leaks != 0) System.exit(1);
    }
}