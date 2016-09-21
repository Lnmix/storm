package storm.mesos.resources;

import org.apache.mesos.Protos;
import java.util.ArrayList;
import java.util.List;
import com.google.common.base.Optional;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;

public class Constraints {

  public static final String OP_LIKE = "LIKE";
  public static final String OP_UNLIKE = "UNLIKE";
  private List<Constraint> constraints = new ArrayList<Constraint>();

  public Constraints(String str) {
    if (str != null) {
      Iterable<String> constraintsStr = Splitter.on(",").split(str);
      for (String constraintStr : constraintsStr) {
        Constraint constraint = new Constraint(constraintStr);
        constraints.add(constraint);
      }
    }
  }

  public Boolean meetsAllConstraints(AggregatedOffers offer) {
    if (constraints.size() > 0) {
      for (Constraint constraint : constraints) {
        if (!constraint.isMatch(offer)) {
          return false;
        }
      }
    }
    return true;
  }

  public int size() {
    return constraints.size();
  }

  public Boolean meetsAllConstraints(Protos.Offer offer) {
    if (constraints.size() > 0) {
      for (Constraint constraint : constraints) {
        if (!constraint.isMatch(offer)) {
          return false;
        }
      }
    }
    return true;
  }

  public static class Constraint {
    private String _field;
    private String _operator;
    private String _value;

    public Constraint(String field, String operator, String value) {
      _field = field;
      _operator = operator;
      _value = value;
    }

    public Constraint(Iterable<String> terms) {
      this(
          terms.iterator().next(),
          Iterables.get(terms, 1),
          Iterables.get(terms, 2)
      );
    }

    public Constraint(String constraint) {
      this(Splitter.on(":").split(constraint));
    }

    public Boolean isMatch(Protos.Offer offer) {
      if (_field.equals("hostname")) {
        return checkValue(offer.getHostname());
      } else {
        return checkAttribute(offer.getAttributesList());
      }
    }

    public Boolean isMatch(AggregatedOffers offer) {
      if (_field.equals("hostname")) {
        return checkValue(offer.getHostname());
      } else {
        return checkAttribute(offer.getAttributes());
      }
    }

    private Boolean checkValue(String value) {
      switch (_operator) {
        case OP_LIKE:
          return value.matches(_value);
        case OP_UNLIKE:
          return !value.matches(_value);
        default:
          return false;
      }
    }

    private Boolean checkAttribute(List<Protos.Attribute> attributes) {
      Optional<Protos.Attribute> attribute = getAttribute(attributes, _field);
      if (!attribute.isPresent()) {
        return false;
      } else {
        return checkValue(getValue(attribute.get()));
      }
    }
  }

  private static Optional<Protos.Attribute> getAttribute(List<Protos.Attribute> attributes, String name) {
    for (Protos.Attribute attribute : attributes) {
      if (attribute.getName().equals(name)) {
        return Optional.of(attribute);
      }
    }
    return Optional.fromNullable(null);
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
