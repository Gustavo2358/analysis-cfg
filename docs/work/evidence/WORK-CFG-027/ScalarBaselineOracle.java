import java.nio.file.Path;
import io.github.gustavo2358.air.json.AirJsonException;
import io.github.gustavo2358.analysis.cfg.adapters.AirJsonFileReader;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;

/** Behavioral oracle executed before repinning or materializing integration fixtures. */
public class ScalarBaselineOracle {
    public static void main(String[] args) throws Exception {
        try {
            var publication = new AirJsonFileReader().read(Path.of(args[0]));
            var result = new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).build(publication, BuildOptions.defaults());
            if (result.status() != CfgBuildResult.Status.CFG_BUILT) throw new AssertionError(result.status());
            System.out.println("GREEN: CFG_BUILT");
        } catch (AirJsonException failure) {
            System.out.println("expected=CFG_BUILT actual=" + failure.code() + " path=" + failure.path());
            if (failure.code() != AirJsonException.Code.IMPLEMENTATION_LIMIT) throw failure;
            throw new AssertionError("Scalar transport boundary rejected by baseline shared codec", failure);
        }
    }
}
