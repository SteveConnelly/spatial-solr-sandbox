package org.apache.lucene.spatial.core.grid.jts;

import org.apache.lucene.spatial.core.Extent;
import org.apache.lucene.spatial.core.IntersectCase;
import org.apache.lucene.spatial.core.Shape;

import com.vividsolutions.jts.geom.Envelope;

public class JtsEnvelope implements Extent
{
  public final Envelope envelope;

  public JtsEnvelope(Envelope envelope)
  {
    this.envelope = envelope;
  }

  public JtsEnvelope(double x1, double x2, double y1, double y2)
  {
    this.envelope = new Envelope( x1,x2,y1,y2 );
  }

  //----------------------------------------
  //----------------------------------------

  public double getArea()
  {
    return getWidth() * getHeight();
  }

  public boolean getCrossesDateLine()
  {
    return false;
  }

  //----------------------------------------
  //----------------------------------------

  @Override
  public double getHeight() {
    return envelope.getWidth();
  }

  @Override
  public double getWidth() {
    return envelope.getHeight();
  }

  @Override
  public double getMaxX() {
    return envelope.getMaxX();
  }

  @Override
  public double getMaxY() {
    return envelope.getMaxY();
  }

  @Override
  public double getMinX() {
    return envelope.getMinX();
  }

  @Override
  public double getMinY() {
    return envelope.getMinY();
  }

  @Override
  public boolean hasSize() {
    return !envelope.isNull();
  }

  //----------------------------------------
  //----------------------------------------

  @Override
  public Extent getExtent() {
    return this;
  }

  @Override
  public IntersectCase intersect(Shape other, Object context)
  {
    if( other instanceof Extent ) {
      Extent ext = other.getExtent();
      if (ext.getMinX() > envelope.getMaxX() ||
          ext.getMaxX() < envelope.getMinX() ||
          ext.getMinY() > envelope.getMaxY() ||
          ext.getMaxY() < envelope.getMinY() ){
        return IntersectCase.OUTSIDE;
      }

      if( ext.getMinX() >= envelope.getMinX() &&
          ext.getMaxX() <= envelope.getMaxX() &&
          ext.getMinY() >= envelope.getMinY() &&
          ext.getMaxY() <= envelope.getMaxY() ){
        return IntersectCase.CONTAINS;
      }

      if( envelope.getMinX() >= ext.getMinY() &&
          envelope.getMaxX() <= ext.getMaxX() &&
          envelope.getMinY() >= ext.getMinY() &&
          envelope.getMaxY() <= ext.getMaxY() ){
        return IntersectCase.WITHIN;
      }
      return IntersectCase.INTERSECTS;
    }
    else if( other instanceof JtsGeometry ) {

    }
    throw new IllegalArgumentException( "JtsEnvelope can be compared with Envelope or Geogmetry" );
  }

  @Override
  public String toString()
  {
    return envelope.toString();
  }
}