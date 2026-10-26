package br.com.livrariaonline.catalog;

final class Isbn13 {
    private Isbn13() {
    }

    static String normalizeAndValidate(String value) {
        if (value == null) {
            throw new InvalidIsbnException();
        }
        String normalized = value.replaceAll("[-\\s]", "");
        if (!normalized.matches("\\d{13}")) {
            throw new InvalidIsbnException();
        }
        int sum = 0;
        for (int index = 0; index < 13; index++) {
            int digit = normalized.charAt(index) - '0';
            sum += digit * (index % 2 == 0 ? 1 : 3);
        }
        if (sum % 10 != 0) {
            throw new InvalidIsbnException();
        }
        return normalized;
    }

    static final class InvalidIsbnException extends IllegalArgumentException {
        InvalidIsbnException() {
            super("ISBN-13 inválido.");
        }
    }
}
