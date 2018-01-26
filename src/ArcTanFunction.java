public class ArcTanFunction implements ICurveSlicer.CurveFunction
{

  @Override
  public double valueAt(final double x, final double[] params)
  {
    final double B0 = params[0];
    final double P = params[1];
    final double Q = params[2];
    final double numerator = Math.sin(B0) + P * x;
    final double denominator = Math.cos(B0) + Q * x;
    return Math.atan2(numerator, denominator);
  }
}
