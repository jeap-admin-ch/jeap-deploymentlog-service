package ch.admin.bit.jeap.deploymentlog.docgen.model;

public enum Color {
    NONE("#NONE"),

    /**
     * Green
     */
    ALL_IDENTICAL("#e3fcef"),
    /**
     * Yellow
     */
    HIGHER_THAN_NEXT_STAGE("#fffae6"),
    /**
     * Blue
     */
    MISSES_NEXT_STAGE("#deebff");

    public final String colorCode;

    Color(String colorCode) {
        this.colorCode = colorCode;
    }
}
