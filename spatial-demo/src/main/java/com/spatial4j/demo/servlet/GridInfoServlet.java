package com.spatial4j.demo.servlet;

import com.spatial4j.core.context.SpatialContextFactory;
import com.spatial4j.core.context.jts.JtsSpatialContext;
import com.spatial4j.core.context.jts.JtsSpatialContextFactory;
import com.spatial4j.core.shape.Shape;
import com.spatial4j.demo.KMLHelper;
import com.spatial4j.demo.app.WicketApplication;
import com.spatial4j.demo.io.SampleData;
import com.spatial4j.demo.io.SampleDataReader;
import de.micromata.opengis.kml.v_2_2_0.Kml;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.lucene.spatial.prefix.tree.Cell;
import org.apache.lucene.spatial.prefix.tree.GeohashPrefixTree;
import org.apache.lucene.spatial.prefix.tree.QuadPrefixTree;
import org.apache.lucene.spatial.prefix.tree.SpatialPrefixTree;
import org.apache.lucene.spatial.query.SpatialArgs;
import org.apache.lucene.spatial.query.SpatialOperation;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class GridInfoServlet extends HttpServlet
{

  @Override
  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  public static int getIntParam( HttpServletRequest req, String p, int defaultValue )
  {
    String v = req.getParameter( p );
    if( v != null && v.length() > 0 ) {
      return Integer.parseInt( v );
    }
    return defaultValue;
  }

  public static double getDoubleParam( HttpServletRequest req, String p, double defaultValue )
  {
    String v = req.getParameter( p );
    if( v != null && v.length() > 0 ) {
      return Double.parseDouble( v );
    }
    return defaultValue;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    doPost(req, resp);
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
    //Initialize the SpatialContext from the request parameters.
    JtsSpatialContext ctx;
    HashMap<String, String> ctxParams = new HashMap<String, String>();
    ctxParams.put("spatialContextFactory", JtsSpatialContextFactory.class.getName());
    ctxParams.put("geo", "true");
    ctxParams.put("autoIndex", "true");
    ctxParams.put("normWrapLongitude", "true");//our country dataset needs this
    for (Map.Entry<String, String[]> entry : req.getParameterMap().entrySet()) {
      ctxParams.put(entry.getKey(), (entry.getValue())[0]);
    }
    ctx = (JtsSpatialContext) SpatialContextFactory.makeSpatialContext(ctxParams, getClass().getClassLoader());

    //
    String name = req.getParameter( "name" );
    Shape shape = null;
    String country = req.getParameter( "country" );
    if( country != null && country.length() == 3 ) {
      InputStream in = WicketApplication.getStreamFromDataResource("countries-poly.txt");
      try {
        SampleDataReader reader = new SampleDataReader( in );
        while( reader.hasNext() ) {
          SampleData data = reader.next();
          if( country.equalsIgnoreCase( data.id ) ) {
            if (StringUtils.isEmpty(name))
              name = data.name;
            shape = ctx.readShapeFromWkt(data.shape);
            break;
          }
        }
      } catch (ParseException e) {
        log(e.toString(), e);
        res.sendError(HttpServletResponse.SC_BAD_REQUEST, e.toString());
        return;
      } finally {
        IOUtils.closeQuietly(in);
      }

      if( shape == null ) {
        res.sendError(HttpServletResponse.SC_BAD_REQUEST, "unable to find: "+country );
        return;
      }
    }
    int depth = getIntParam( req, "depth", 16 );

    String gridtype = req.getParameter("gridType");
    
    SpatialPrefixTree grid;
    if ("geohash".equals(gridtype)) {
      grid = new GeohashPrefixTree(ctx, depth);
    } else if ("quad".equals(gridtype)) {
      grid = new QuadPrefixTree( ctx, depth );
    } else {
      res.sendError(HttpServletResponse.SC_BAD_REQUEST, "unknown grid type: "+gridtype );
      return;
    }
   

    // If they don't set a country, then use the input
    if( shape == null ) {
      String geo = req.getParameter( "geometry" );
      if( geo == null ) {
        res.sendError(HttpServletResponse.SC_BAD_REQUEST, "missing parameter: 'geometry'" );
        return;
      }
      try {
        shape = ctx.readShapeFromWkt(geo);
      }
      catch( Exception ex ) {
        ex.printStackTrace();
        res.sendError(HttpServletResponse.SC_BAD_REQUEST, "error parsing geo: "+ex );
        return;
      }
    }
    SpatialArgs args = new SpatialArgs(SpatialOperation.Intersects, shape);
    double distErrPct = getDoubleParam(req, "distErrPct", SpatialArgs.DEFAULT_DISTERRPCT);
    double distErr = args.resolveDistErr(grid.getSpatialContext(), distErrPct);
    int detailLevel = grid.getLevelForDistance(distErr);
    List<Cell> nodes = grid.getCells(shape, detailLevel, false, true);

    int biggestLevel = 100;
    for (Cell node : nodes) {
      biggestLevel = Math.min(biggestLevel, node.getLevel());
    }
    String msg = "Using detail level " + detailLevel + " (biggest is " + biggestLevel + ") yielding " + nodes.size() + " tokens.";
    log(msg);

    List<String> info = SpatialPrefixTree.cellsToTokenStrings(nodes);
    String format = req.getParameter( "format" );
    if( "kml".equals( format ) ) {
      if( name == null || name.length() < 2 ) {
        name = "KML - "+new Date( System.currentTimeMillis() );
      }
      Kml kml = KMLHelper.toKML( name, grid, info );

      res.setHeader("Content-Disposition","attachment; filename=\"" + name + ".kml\";");
      res.setContentType( "application/vnd.google-earth.kml+xml" );
      kml.marshal( res.getOutputStream() );
      return;
    }

    res.setContentType( "text/plain" );
    PrintStream out = new PrintStream( res.getOutputStream() );
    out.println(msg);
    out.println( info.toString() );
  }
}
