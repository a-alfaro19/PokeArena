package cr.ac.tec.pokearena.application.usecase;

import cr.ac.tec.pokearena.application.dto.CreateLocationCommand;
import cr.ac.tec.pokearena.domain.model.Location;
import cr.ac.tec.pokearena.domain.repository.LocationRepository;

public class CreateLocationUseCase {
    private final LocationRepository locationRepository;

    public CreateLocationUseCase(LocationRepository locationRepository) {
        this.locationRepository = locationRepository;
    }

    public Location execute(CreateLocationCommand command) {
        Location location = new Location(null, command.name(), command.description());
        return locationRepository.save(location);
    }
}
