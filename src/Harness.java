import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

public class Harness
{

  public static void doAssert(final Boolean cond, final String errMsg)
  {
    if (!cond)
    {
      throw new IllegalArgumentException(errMsg);
    }
  }

  /**
   * @param args
   * @throws ParseException
   * @throws IOException
   * @throws NumberFormatException
   */
  public static void main(String[] args) throws NumberFormatException,
      IOException, ParseException
  {
    String line;

    final ArrayList<Double> values = new ArrayList<Double>();
    final ArrayList<Long> timeStamps = new ArrayList<Long>();

    doAssert(args.length == 1,
        "Please provide a single parameter which is the name of the file to process");

    final File f = new File(args[0]);
    doAssert(f.exists(), String.format("File %s doesn't exist!", args[0]));

    System.out.format("Processing: %s\n", args[0]);

    final DateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
    df.setTimeZone(TimeZone.getTimeZone("GMT"));

    final BufferedReader br = new BufferedReader(new FileReader(args[0]));

    // ok, skip the header line
    br.readLine();

    while ((line = br.readLine()) != null)
    {
      if (line.isEmpty())
      {
        continue;
      }
      final String[] cells = line.split(",");
      doAssert(cells.length == 6, "Problem with input file format");

      final long dtg = df.parse(cells[0]).getTime();
      final double brg = Double.parseDouble(cells[3]);

      timeStamps.add(dtg);
      values.add(brg);
    }
    br.close();

    final ICurveSlicer slicer = new CurveSlicer(timeStamps, values);

    final ICurveSlicer.CurveFunction curve = new ArcTanFunction();
    final double fitValue = 0;
    final long minLegLength = 5 * 60 * 1000; // 5 mins

    final List<ICurveSlicer.Zone> zones = slicer.getZones(curve, fitValue, minLegLength);

    if (zones != null)
    {
      for (final ICurveSlicer.Zone zone : zones)
      {
        System.out.println(zone.getStart() + " - " + zone.getEnd());
      }
    }

  }

}
