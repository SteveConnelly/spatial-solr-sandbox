/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.spatial.pending.jts;

import com.spatial4j.core.context.jts.JtsSpatialContext;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKBReader;
import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.search.DocIdSet;
import org.apache.lucene.search.Filter;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.OpenBitSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;


public class GeometryOperationFilter extends Filter {

  static final Logger log = LoggerFactory.getLogger(GeometryOperationFilter.class);

  final String fieldName;
  final JtsSpatialContext ctx;
  final GeometryTest tester;
  final WKBReader wkbReader;
  final BytesRefStream bstream;

  public GeometryOperationFilter(String fieldName, GeometryTest tester, JtsSpatialContext ctx) {
    this.fieldName = fieldName;
    this.ctx = ctx;
    this.tester = tester;
    this.wkbReader = new WKBReader(ctx.getGeometryFactory());

    BytesRef bytes = new BytesRef(10000);
    this.bstream = new BytesRefStream(bytes);
  }

  @Override
  public DocIdSet getDocIdSet(final AtomicReaderContext context, final Bits acceptDocs) throws IOException {
    AtomicReader areader = context.reader();

    SortedDocValues sortedDocValues = areader.getSortedDocValues(fieldName);
    if (sortedDocValues == null)
      return null;

    OpenBitSet bits = new OpenBitSet(areader.maxDoc());

    BytesRef bytes = bstream.getBytesRef();
    for( int docID=0; docID<areader.maxDoc(); docID++ ) {
      if( acceptDocs == null || acceptDocs.get(docID)) {
        sortedDocValues.get(docID, bytes);
        if(bytes.length > 0) {
          try {
            bstream.setBytesRef(bytes); // likely the same
            Geometry geo = wkbReader.read(bstream);

            if (tester.matches(geo)) {
              bits.set(docID);
            }
          }
          catch (ParseException ex) {
            log.warn("error reading indexed geometry", ex);
          }
        }
      }
    }
    return bits;
  }
}
