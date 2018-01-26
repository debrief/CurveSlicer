import java.util.List;

public interface ICurveSlicer
{

  public interface CurveFunction
  {
    /**
     * work out the value at the supplied time
     * 
     * @param dtg
     * @param params
     * @return
     */
    double valueAt(final double x, final double[] params);
  }

  /**
   * return the set of zones that match the supplied curve
   * 
   * @param function
   *          the curve
   * @param fit
   *          allowable error on the curve
   * @param minLeg
   *          minimum leg length (millis)
   * @return list of zones that fit the curve
   */
  List<Zone> getZones(final CurveFunction function, final double fit,
      final long minLeg);

  
  public static class Zone
  {
    private final long start;
    private final long end;

    public Zone(final long start, final long end)
    {
      this.start = start;
      this.end = end;
    }

    public long getStart()
    {
      return start;
    }

    public long getEnd()
    {
      return end;
    }
  }
}
