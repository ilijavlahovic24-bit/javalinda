package rs.ac.bg.etf.kdp.protocol;

import java.io.Serializable;

/**
 * Poruka koju LindaSocketClient salje ka TupleSpaceServer-u za jednu od
 * pet ne-eval operacija. Odgovor server salje kao TupleResponse.
 */
public class TupleMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Op { OUT, IN, INP, RD, RDP }

    private final Op operation;
    private final String[] tuple;

    public TupleMessage(Op operation, String[] tuple) {
        this.operation = operation;
        this.tuple = tuple;
    }

    public Op getOperation() {
        return operation;
    }

    public String[] getTuple() {
        return tuple;
    }
}