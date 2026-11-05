package cr.ac.tec.pokearena.domain.model;

public class Location {
    private Long id;
    private String name;
    private String description;

    public Location(Long id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

}
