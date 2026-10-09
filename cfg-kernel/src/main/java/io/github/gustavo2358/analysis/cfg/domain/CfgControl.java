package io.github.gustavo2358.analysis.cfg.domain;

import io.github.gustavo2358.air.model.Control;
import io.github.gustavo2358.air.model.Ids.CompletionPortId;
import io.github.gustavo2358.air.model.Ids.LabelId;
import io.github.gustavo2358.air.model.Ids.OperationId;
import io.github.gustavo2358.air.model.Operations;
import io.github.gustavo2358.air.model.Scopes;
import io.github.gustavo2358.air.model.Terminator;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Compact structural control retained by the CFG. Expression, operand, effect and fallback
 * payload remains owned by the program store instead of being duplicated in graph nodes.
 */
public sealed interface CfgControl permits CfgControl.Jump, CfgControl.Branch,
        CfgControl.Return, CfgControl.Halt, CfgControl.Invoke, CfgControl.Opaque,
        CfgControl.LocalInvoke, CfgControl.LocalBoundary, CfgControl.LocalResume,
        CfgControl.LocalUnwind, CfgControl.Unsupported {
    OperationId operation();

    record Jump(OperationId operation, LabelId destination) implements CfgControl {
        public Jump { Objects.requireNonNull(operation); Objects.requireNonNull(destination); }
    }
    record Branch(OperationId operation, LabelId trueDestination, LabelId falseDestination)
            implements CfgControl {
        public Branch {
            Objects.requireNonNull(operation); Objects.requireNonNull(trueDestination);
            Objects.requireNonNull(falseDestination);
        }
    }
    record Return(OperationId operation) implements CfgControl {
        public Return { Objects.requireNonNull(operation); }
    }
    record Halt(OperationId operation, Operations.HaltKind haltKind) implements CfgControl {
        public Halt { Objects.requireNonNull(operation); Objects.requireNonNull(haltKind); }
    }
    record Invoke(OperationId operation, List<Control.InvocationAlternative> alternatives,
                  Scopes.ControlBound remainder) implements CfgControl {
        public Invoke {
            Objects.requireNonNull(operation); alternatives = List.copyOf(alternatives);
            Objects.requireNonNull(remainder);
        }
    }
    record Opaque(OperationId operation, List<Control.ControlAlternative> alternatives,
                  Scopes.ControlBound remainder) implements CfgControl {
        public Opaque {
            Objects.requireNonNull(operation); alternatives = List.copyOf(alternatives);
            Objects.requireNonNull(remainder);
        }
    }
    record ReentryGuard(String activationKey, LabelId destination) {
        public ReentryGuard { Objects.requireNonNull(activationKey); Objects.requireNonNull(destination); }
    }
    record ResumeRoute(String key, LabelId destination) {
        public ResumeRoute { Objects.requireNonNull(key); Objects.requireNonNull(destination); }
    }
    record LocalInvoke(OperationId operation, LabelId entry, List<CompletionPortId> completionPorts,
                       LabelId resume, Optional<ReentryGuard> reentryGuard,
                       List<ResumeRoute> resumeRoutes) implements CfgControl {
        public LocalInvoke {
            Objects.requireNonNull(operation); Objects.requireNonNull(entry);
            completionPorts = List.copyOf(completionPorts); Objects.requireNonNull(resume);
            reentryGuard = Objects.requireNonNull(reentryGuard); resumeRoutes = List.copyOf(resumeRoutes);
        }
    }
    record LocalBoundary(OperationId operation, CompletionPortId port, LabelId defaultDestination,
                         Optional<String> resumeKey) implements CfgControl {
        public LocalBoundary {
            Objects.requireNonNull(operation); Objects.requireNonNull(port);
            Objects.requireNonNull(defaultDestination); resumeKey = Objects.requireNonNull(resumeKey);
        }
    }
    record LocalResume(OperationId operation, Optional<String> resumeKey) implements CfgControl {
        public LocalResume { Objects.requireNonNull(operation); resumeKey = Objects.requireNonNull(resumeKey); }
    }
    record LocalUnwind(OperationId operation, BigInteger count, LabelId destination, boolean all)
            implements CfgControl {
        public LocalUnwind {
            Objects.requireNonNull(operation); Objects.requireNonNull(count);
            Objects.requireNonNull(destination);
        }
    }
    /** Unknown structural semantics admitted only by PARTIAL_ANALYSIS. */
    record Unsupported(OperationId operation) implements CfgControl {
        public Unsupported { Objects.requireNonNull(operation); }
    }

    static CfgControl from(Terminator terminator) {
        Objects.requireNonNull(terminator, "terminator");
        OperationId operation = terminator.header().id();
        return switch (terminator) {
            case Operations.Jump jump -> new Jump(operation, jump.destination());
            case Operations.Branch branch -> new Branch(operation, branch.trueDestination(), branch.falseDestination());
            case Operations.Return ignored -> new Return(operation);
            case Operations.Halt halt -> new Halt(operation, halt.haltKind());
            case Operations.Invoke invoke -> new Invoke(operation, invoke.outcomes().known(), invoke.outcomes().remainder());
            case Operations.Opaque opaque -> new Opaque(operation, opaque.envelope().control().known(),
                    opaque.envelope().control().remainder());
            case Operations.LocalInvoke invoke -> new LocalInvoke(operation, invoke.entry(),
                    invoke.completionPorts(), invoke.resume(),
                    invoke.reentryGuard().map(guard -> new ReentryGuard(guard.activationKey(), guard.destination())),
                    invoke.resumeRoutes().stream().map(route -> new ResumeRoute(route.key(), route.destination())).toList());
            case Operations.LocalBoundary boundary -> new LocalBoundary(operation, boundary.port(),
                    boundary.defaultDestination(), boundary.resumeKey());
            case Operations.LocalResume resume -> new LocalResume(operation, resume.resumeKey());
            case Operations.LocalUnwind unwind -> new LocalUnwind(operation, unwind.count(),
                    unwind.destination(), unwind.all());
            default -> new Unsupported(operation);
        };
    }

    static boolean local(CfgControl control) {
        return control instanceof LocalInvoke || control instanceof LocalBoundary
                || control instanceof LocalResume || control instanceof LocalUnwind;
    }

    static List<Control.ControlAlternative> alternatives(CfgControl control) {
        if (control instanceof LocalResume
                || control instanceof LocalBoundary boundary && boundary.resumeKey().isPresent())
            return List.of(new Control.Exceptional("invalid_local_return", Control.Propagate.INSTANCE));
        if (control instanceof LocalUnwind)
            return List.of(new Control.Exceptional("invalid_local_unwind", Control.Propagate.INSTANCE));
        if (control instanceof Invoke invoke) return new ArrayList<>(invoke.alternatives());
        if (control instanceof Opaque opaque) return opaque.alternatives();
        return List.of();
    }

    static Scopes.ControlBound remainder(CfgControl control) {
        if (control instanceof Invoke invoke) return invoke.remainder();
        if (control instanceof Opaque opaque) return opaque.remainder();
        return Scopes.NoControl.INSTANCE;
    }
}
