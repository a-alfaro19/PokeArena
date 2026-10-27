package cr.ac.tec.pokearena;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@org.springframework.test.context.ContextConfiguration(initializers = DisposableDatabaseInitializer.class)
@SpringBootTest
class PokearenaApplicationTests {

	@Test
	void contextLoads() {
	}

}
