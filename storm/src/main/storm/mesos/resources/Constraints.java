package storm.mesos.resources;

import org.apache.mesos.Protos;
import storm.mesos.resources.AggregatedOffers;
import java.util.List;
import com.google.common.base.Optional;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;

public class Constraints {

    public static final String OP_LIKE = "LIKE";
    public static final String OP_UNLIKE = "UNLIKE";

    public static String CONF_TOPOLOGY_MESOS_CONSTRAINTS = "topology.mesos.constraints";

    private List<Constraint> constraints;

    public Constraints(Iterable<String> constraints) {
        for (String str : constraints) {
            Constraint constraint = new Constraint(str);
            this.constraints.add(constraint);
        }
    }

    public Constraints(String constraints) {
        this(Splitter.on(",").split(constraints));
    }

    public Boolean meetsAllConstraints(AggregatedOffers offer) {
        for (Constraint constraint : this.constraints) {
            if (!constraint.isMatch(offer)) {
                return false;
            }
        }
        return true;
    }

    public Boolean meetsAllConstraints(Protos.Offer offer) {
        for (Constraint constraint : this.constraints) {
            if (!constraint.isMatch(offer)) {
                return false;
            }
        }
        return true;
    }

    public static class Constraint {
        private String field;
        private String operator;
        private String value;

        public Constraint(String field, String operator, String value) {
            this.field = field;
            this.operator = operator;
            this.value = value;
        }

        public Constraint(Iterable<String> terms) {
            this(
                terms.iterator().next(),
                terms.iterator().next(),
                terms.iterator().next()
            );
        }

        public Constraint(String constraint) {
            this(Splitter.on(":").split(constraint));
        }

        public Boolean isMatch(Protos.Offer offer) {
            if (this.field == "hostname") {
                return checkValue(this, offer.getHostname());
            } else {
                return checkAttribute(this, offer.getAttributesList());
            }
        }

        public Boolean isMatch(AggregatedOffers offer) {
            if (this.field == "hostname") {
                return checkValue(this, offer.getHostname());
            } else {
                return checkAttribute(this, offer.getAttributes());
            }
        }
    }
    private static Optional<Protos.Attribute> getAttribute(List<Protos.Attribute> attributes, String name) {
        for (Protos.Attribute attribute : attributes) {
            if (attribute.getName() == name) {
                return Optional.of(attribute);
            }
        }
        return Optional.fromNullable(null);
    }

    private static Boolean checkAttribute(Constraint constraint, List<Protos.Attribute> attributes) {
        Optional<Protos.Attribute> attribute = getAttribute(attributes, constraint.field);
        if (!attribute.isPresent()) {
            return false;
        } else {
            return checkValue(constraint, getValue(attribute.get()));
        }
    }

    private static Boolean checkValue(Constraint constraint, String value) {
        switch (constraint.operator) {
            case OP_LIKE:
                return value.matches(constraint.value);
            case OP_UNLIKE:
                return !value.matches(constraint.value);
            default:
                return false;
        }
    }

    private static String getValue(Protos.Attribute attribute) {
        Protos.Value.Type type = attribute.getType();
        switch (type) {
            case SCALAR:
                return java.text.NumberFormat.getInstance().format(attribute.getScalar().getValue());
            case TEXT:
                return attribute.getText().getValue();
            case RANGES:
                return Joiner.on(",")
                    .join(Collections2.transform(
                        attribute.getRanges().getRangeList(), 
                        new Function<Protos.Value.Range, String>() {
                            @Override
                            public String apply(final Protos.Value.Range range) {
                                return String.valueOf(range.getBegin()) + "-" + String.valueOf(range.getEnd());
                            }
                        }
                    ));
            case SET:
                return Joiner.on(",").join(attribute.getSet().getItemList());
            default:
                return "";
        }
    }
}