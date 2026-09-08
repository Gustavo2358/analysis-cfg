package io.github.gustavo2358.analysis.cfg.launcher;

import io.github.gustavo2358.air.json.AirJsonException;
import io.github.gustavo2358.air.model.Publication;
import io.github.gustavo2358.analysis.cfg.adapters.AirInputLimitException;
import io.github.gustavo2358.analysis.cfg.adapters.AirJsonFileReader;
import io.github.gustavo2358.analysis.cfg.adapters.CfgJsonException;
import io.github.gustavo2358.analysis.cfg.adapters.CfgJsonWriter;
import io.github.gustavo2358.analysis.cfg.application.BuildCfg;
import io.github.gustavo2358.analysis.cfg.application.BuildOptions;
import io.github.gustavo2358.analysis.cfg.application.CfgBuildCoordinator;
import io.github.gustavo2358.analysis.cfg.application.CfgBuildResult;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/** Two positional arguments, explicit composition and expected failure exit codes; no CFG rules. */
public final class AnalysisCfg {
    private AnalysisCfg() { }

    public static void main(String[] args) { System.exit(run(args, System.err)); }

    public static int run(String[] args, PrintStream err) {
        return run(args, err, new AirJsonFileReader(),
                new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()), new CfgJsonWriter());
    }

    static int run(String[] args, PrintStream err, AirJsonFileReader reader, BuildCfg builder, CfgJsonWriter writer) {
        if (args.length != 2 || args[0].isEmpty() || args[1].isEmpty()) return usage(err);
        Path input;
        Path output;
        try { input = Path.of(args[0]); output = Path.of(args[1]); }
        catch (InvalidPathException invalid) { return usage(err); }
        Publication publication;
        try { publication = reader.read(input); }
        catch (AirInputLimitException limit) {
            err.println("AIR IMPLEMENTATION_LIMIT path=$ maximumDocumentBytes=" + limit.maximumDocumentBytes());
            return 3;
        } catch (AirJsonException invalid) {
            err.println("AIR " + invalid.code() + " path=" + line(invalid.path()) + " " + line(invalid.getMessage()));
            for (var issue : invalid.issues()) err.println("  " + issue.kind() + " " + line(issue.rule())
                    + " subject=" + line(issue.subject()) + " " + line(issue.detail()));
            return 3;
        } catch (IOException failure) {
            err.println("AIR INPUT_IO: " + line(failure.getMessage()));
            return 3;
        }
        CfgBuildResult result = builder.build(publication, BuildOptions.defaults());
        if (result.status() != CfgBuildResult.Status.CFG_BUILT) {
            err.println("CFG " + result.status());
            for (var issue : result.projectionIssues())
                err.println("  projectionIssue=" + issue.code() + " subject=" + line(issue.subject()));
            for (var capability : result.unsupportedCapabilities())
                err.println("  unsupportedCapability=" + line(capability.name()) + "@" + capability.version());
            for (var issue : result.preflight().issues())
                err.println("  " + issue.kind() + " " + line(issue.rule()) + " " + line(issue.detail()));
            return 4;
        }
        try { writer.write(result, output); }
        catch (CfgJsonException failure) {
            err.println("CFG OUTPUT_SERIALIZATION: " + line(failure.getMessage()));
            return 5;
        } catch (IOException failure) {
            err.println("CFG OUTPUT_IO: " + line(failure.getMessage()));
            return 6;
        }
        return 0;
    }

    private static int usage(PrintStream err) {
        err.println("usage: analysis-cfg <air.json> <cfg.json>");
        return 2;
    }

    /** Human stderr only, not wire authority. Keep input-derived diagnostics on one bounded line. */
    private static String line(Object value) {
        String text = String.valueOf(value).replaceAll("[\\p{Cntrl}]", " ");
        return text.length() <= 400 ? text : text.substring(0, 400) + "…";
    }
}
