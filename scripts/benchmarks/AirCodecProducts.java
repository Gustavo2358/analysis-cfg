package io.github.gustavo2358.analysis.cfg.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.analysis.adapters.DataflowAirReader;
import io.github.gustavo2358.analysis.adapters.DependencyInputJson;
import io.github.gustavo2358.analysis.adapters.DependencyJson;
import io.github.gustavo2358.analysis.cfg.adapters.CfgJsonWriter;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import io.github.gustavo2358.analysis.dependencies.DependencyAnalysis;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/** Standalone probe: input directory, output directory, products|decode|bundle, iterations.
 * Run each codec classpath in a separate JVM. Products are saved for complete byte comparison.
 */
public final class AirCodecProducts {
    private AirCodecProducts() {}
    public static void main(String[] args) throws Exception {
        Path input = Path.of(args[0]), output = Path.of(args[1]);
        Files.createDirectories(output);
        for (int i = 0; i < Integer.parseInt(args[3]); i++) {
            var row = new LinkedHashMap<String, Object>();
            row.put("iteration", i);
            long start = System.nanoTime();
            if (args[2].equals("bundle")) {
                var bundle = new DependencyInputJson().read(input.resolve("dependency-input.json"),
                        DataflowAirReader.forPartialAnalysis());
                row.put("readDecodeMs", elapsed(start));
                long analysis = System.nanoTime();
                var dependencies = new DependencyAnalysis().prepare(bundle);
                row.put("dependencyMs", elapsed(analysis));
                var bytes = new ByteArrayOutputStream();
                new DependencyJson().write(dependencies, bytes);
                save(output, "dependencies.json", bytes.toByteArray(), row);
            } else {
                byte[] inputBytes = Files.readAllBytes(input.resolve("program.air.json"));
                row.put("readMs", elapsed(start));
                long decode = System.nanoTime();
                var decoded = new AirJson().decodeForPartialAnalysis(inputBytes);
                row.put("decodeMs", elapsed(decode));
                row.put("airBytes", inputBytes.length);
                if (args[2].equals("products")) {
                    var options = BuildOptions.defaults();
                    var cfg = new CfgBuildCoordinator(SemanticInterpreterRegistry.empty())
                            .build(decoded.publication(), options);
                    save(output, "cfg.json", new CfgJsonWriter().encode(cfg), row);
                    var encoded = new AirJson().encodeForPartialAnalysis(decoded.publication());
                    if (!decoded.validation().equals(encoded.validation()))
                        throw new AssertionError("Validation changed during re-encoding");
                    save(output, "canonical.air.json", encoded.bytes(), row);
                    var validation = decoded.validation();
                    var diagnostic = new LinkedHashMap<String, Object>();
                    diagnostic.put("status", validation.status());
                    diagnostic.put("statistics", validation.statistics());
                    diagnostic.put("counts", new java.util.TreeMap<>(validation.diagnostics().counts()));
                    diagnostic.put("traversalCompleted", validation.diagnostics().traversalCompleted());
                    diagnostic.put("issues", validation.issues().stream().map(issue -> java.util.List.of(
                            issue.kind().name(), issue.rule(), issue.subject().map(Object::toString).orElse(""),
                            issue.detail())).toList());
                    save(output, "validation.json", new ObjectMapper().writeValueAsBytes(diagnostic), row);
                } else if (!args[2].equals("decode")) throw new IllegalArgumentException("Unknown mode");
            }
            row.put("totalMs", elapsed(start));
            System.out.println(new ObjectMapper().writeValueAsString(row));
        }
    }
    private static double elapsed(long start) { return (System.nanoTime() - start) / 1e6; }
    private static void save(Path output, String name, byte[] bytes, Map<String, Object> row) throws Exception {
        Files.write(output.resolve(name), bytes);
        row.put(name + "Bytes", bytes.length);
        row.put(name + "Sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
    }
}
