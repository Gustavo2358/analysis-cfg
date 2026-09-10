package io.github.gustavo2358.analysis.adapters;

import java.util.Objects;

/** External result of one local publication attempt; never included in the prepared payload. */
public record DeliveryReceipt(String resultId,String resultSha256,String destination,Status status,Reason reason) {
    public enum Status { COMPLETE, FAILED }
    public enum Reason { ENCODING_FAILED, WRITE_FAILED, FINALIZATION_FAILED }
    public DeliveryReceipt {
        Objects.requireNonNull(resultId);Objects.requireNonNull(destination);Objects.requireNonNull(status);
        if(resultSha256!=null&&!resultSha256.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("invalid result SHA-256");
        if(status==Status.COMPLETE?(reason!=null||resultSha256==null):reason==null)throw new IllegalArgumentException("delivery shape");
    }
}
