package io.github.gustavo2358.analysis.application;

import io.github.gustavo2358.analysis.plan.*;
import io.github.gustavo2358.analysis.consumers.FactConsumer;
import java.util.*;

/** A registration ID is unique, even for equivalent repeated registrations; duplicates are planning errors. */
public record ConsumerRegistration<F>(ConsumerPlan dependencies, List<SiteInterest> interests,
                                       List<ObservationRequest<?,?>> requests, FactConsumer<F> consumer) {
    public ConsumerRegistration {
        Objects.requireNonNull(dependencies); Objects.requireNonNull(consumer);
        interests = List.copyOf(interests); requests = List.copyOf(requests);
    }
}
