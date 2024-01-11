package vc.live.dto.enums;


public enum Connectiontype {

    JOIN("JOIN"),

    LEAVE("LEAVE");

    private final String literal;

    Connectiontype(String literal) {
        this.literal = literal;
    }
}
