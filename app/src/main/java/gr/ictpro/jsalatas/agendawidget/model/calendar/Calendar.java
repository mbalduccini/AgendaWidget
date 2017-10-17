package gr.ictpro.jsalatas.agendawidget.model.calendar;

public class Calendar {
    private Long id;

    private String accountName;

    private String name;

    private int color;

    Calendar(Long id, String accountName, String name, int color) {
        this.id = id;
        this.accountName = accountName;
        this.name = name;
        this.color = color;
    }

    public Long getId() {
        return id;
    }

    public String getAccountName() {
        return accountName;
    }

    public String getName() {
        return name;
    }

    public int getColor() {
        return color;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Calendar calendar = (Calendar) o;

        return id.equals(calendar.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}