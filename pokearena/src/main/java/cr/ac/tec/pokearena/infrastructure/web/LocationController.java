package cr.ac.tec.pokearena.infrastructure.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cr.ac.tec.pokearena.application.dto.CreateLocationCommand;
import cr.ac.tec.pokearena.application.usecase.CreateLocationUseCase;
import cr.ac.tec.pokearena.domain.model.Location;

@RestController
@RequestMapping("/api/locations")
public class LocationController {
    private final CreateLocationUseCase createLocationUseCase;

    public LocationController(CreateLocationUseCase createLocationUseCase) {
        this.createLocationUseCase = createLocationUseCase;
    }

    @PostMapping
    public ResponseEntity<Location> create(@RequestBody CreateLocationCommand command) {
        Location createdLocation = createLocationUseCase.execute(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdLocation);
    }
}
