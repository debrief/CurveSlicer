import java.util.List;

public class CurveSlicer implements ICurveSlicer
{

  @SuppressWarnings("unused")
  private final List<Long> timeStamps;
  @SuppressWarnings("unused")
  private final List<Double> values;

  public CurveSlicer(final List<Long> timeStamps, final List<Double> values)
  {
    this.timeStamps = timeStamps;
    this.values = values;
  }

  @Override
  public List<Zone> getZones(final CurveFunction function, final double fit, final long minLeg)
  {
    // TODO Auto-generated method stub
    return null;
  }
}
