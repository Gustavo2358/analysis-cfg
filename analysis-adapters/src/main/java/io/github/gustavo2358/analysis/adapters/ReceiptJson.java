package io.github.gustavo2358.analysis.adapters;

import java.io.*;

/** Separate delivery document, never a member of the prepared result. */
public final class ReceiptJson {
    public void write(DeliveryReceipt receipt,OutputStream output)throws IOException {
        String reason=receipt.reason()==null?null:switch(receipt.reason()){case ENCODING_FAILED->"ENCODING_FAILED";case WRITE_FAILED->"WRITE_FAILED";case FINALIZATION_FAILED->"FINALIZATION_FAILED";};
        var json=new JsonOutput(output);json.value(ResultJson.object("schema","analysis-delivery-receipt","version","1.0.0","resultId",receipt.resultId(),"resultSha256",receipt.resultSha256(),"destination",receipt.destination(),
            "status",receipt.status()==DeliveryReceipt.Status.COMPLETE?"COMPLETE":"FAILED","reason",reason));json.finish();
    }
}
