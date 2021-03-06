/*
 This software was produced for the U. S. Government
 under Contract No. W15P7T-11-C-F600, and is
 subject to the Rights in Noncommercial Computer Software
 and Noncommercial Computer Software Documentation
 Clause 252.227-7014 (JUN 1995)

 Copyright 2013 The MITRE Corporation. All Rights Reserved.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package org.opensextant.solrtexttagger;

import org.apache.solr.request.SolrQueryRequest;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * The original test for {@link org.opensextant.solrtexttagger.TaggerRequestHandler}.
 */
public class TaggerTest extends AbstractTaggerTest {

  @BeforeClass
  public static void beforeClass() throws Exception {
    initCore("solrconfig.xml", "schema.xml");
  }

  private void indexAndBuild() throws Exception {
    N[] names = N.values();
    String[] namesStrs = new String[names.length];
    for (int i = 0; i < names.length; i++) {
      namesStrs[i] = names[i].getName();
    }
    buildNames(namesStrs);
  }

  /** Name corpus */
  enum N {
    //keep order to retain ord()
    London, London_Business_School, Boston, City_of_London,
    of, the//filtered out of the corpus by a custom query
    ;

    String getName() { return name().replace('_',' '); }
    static N lookupByName(String name) { return N.valueOf(name.replace(' ', '_')); }
    int getId() { return ordinal(); }
  }

  @Test
  public void testFormat() throws Exception {
    baseParams.set("qt", "/tagPartial");
    baseParams.set("overlaps", "NO_SUB");
    indexAndBuild();

    String rspStr = _testFormatRequest(false);
    String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        "<response>\n" +
        "<int name=\"tagsCount\">1</int>" +
        "<arr name=\"tags\"><lst>" +
          "<int name=\"startOffset\">0</int>" +
          "<int name=\"endOffset\">6</int>" +
          "<arr name=\"ids\"><str>1</str></arr>" +
        "</lst></arr>" +
        "<result name=\"response\" numFound=\"1\" start=\"0\">" +
          "<doc><str name=\"id\">1</str><str name=\"name\">London Business School</str></doc>" +
        "</result>\n" +
        "</response>\n";
    assertEquals(expected, rspStr);
  }

  @Test
  public void testFormatMatchText() throws Exception {
    baseParams.set("qt", "/tagPartial");
    baseParams.set("overlaps", "NO_SUB");
    indexAndBuild();

    String rspStr = _testFormatRequest(true);
    String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        "<response>\n" +
        "<int name=\"tagsCount\">1</int>" +
        "<arr name=\"tags\"><lst>" +
          "<int name=\"startOffset\">0</int>" +
          "<int name=\"endOffset\">6</int><" +
          "str name=\"matchText\">school</str>" +
          "<arr name=\"ids\"><str>1</str></arr>" +
        "</lst></arr>" +
        "<result name=\"response\" numFound=\"1\" start=\"0\">" +
          "<doc><str name=\"id\">1</str><str name=\"name\">London Business School</str></doc>" +
        "</result>\n" +
        "</response>\n";
    assertEquals(expected, rspStr);
  }

  private String _testFormatRequest(boolean matchText) throws Exception {
    String doc = "school";//just one tag
    SolrQueryRequest req = reqDoc(doc, "indent", "off", "omitHeader", "on", "matchText", ""+matchText);
    String rspStr = h.query(req);
    req.close();
    return rspStr;
  }

  @Test
  /** Partial matching, no sub-tags */
  public void testPartialMatching() throws Exception {
    baseParams.set("qt", "/tagPartial");
    baseParams.set("overlaps", "NO_SUB");
    indexAndBuild();

    //these match nothing
    assertTags(reqDoc("") );
    assertTags(reqDoc(" ") );
    assertTags(reqDoc("the") );

    String doc;

    //just London Business School via "school" substring
    doc = "school";
    assertTags(reqDoc(doc), tt(doc,"school", 0, N.London_Business_School));

    doc = "a school";
    assertTags(reqDoc(doc), tt(doc,"school", 0, N.London_Business_School));

    doc = "school a";
    assertTags(reqDoc(doc), tt(doc,"school", 0, N.London_Business_School));

    //More interesting

    doc = "school City";
    assertTags(reqDoc(doc),
        tt(doc, "school", 0, N.London_Business_School),
        tt(doc, "City", 0, N.City_of_London) );

    doc = "City of London Business School";
    assertTags(reqDoc(doc),   //no plain London (sub-tag)
        tt(doc, "City of London", 0, N.City_of_London),
        tt(doc, "London Business School", 0, N.London_Business_School));
  }

  @Test
  /** whole matching, no sub-tags */
  public void testWholeMatching() throws Exception {
    baseParams.set("qt", "/tag");
    baseParams.set("overlaps", "NO_SUB");
    indexAndBuild();

    //these match nothing
    assertTags(reqDoc(""));
    assertTags(reqDoc(" ") );
    assertTags(reqDoc("the") );

    //partial on N.London_Business_School matches nothing
    assertTags(reqDoc("school") );
    assertTags(reqDoc("a school") );
    assertTags(reqDoc("school a") );
    assertTags(reqDoc("school City") );

    String doc;

    doc = "school business london";//backwards
    assertTags(reqDoc(doc), tt(doc,"london", 0, N.London));

    doc = "of London Business School";
    assertTags(reqDoc(doc),   //no plain London (sub-tag)
        tt(doc, "London Business School", 0, N.London_Business_School));

    //More interesting
    doc = "City of London Business School";
    assertTags(reqDoc(doc),   //no plain London (sub-tag)
        tt(doc, "City of London", 0, N.City_of_London),
        tt(doc, "London Business School", 0, N.London_Business_School));

    doc = "City of London Business";
    assertTags(reqDoc(doc),   //no plain London (sub-tag) no Business (partial-match)
        tt(doc, "City of London", 0, N.City_of_London));

    doc = "London Business magazine";
    assertTags(reqDoc(doc),  //Just London; L.B.S. fails
        tt(doc, "London", 0, N.London));
  }

  @Test
  /** whole matching, with sub-tags */
  public void testSubTags() throws Exception {
    baseParams.set("qt", "/tag");
    baseParams.set("overlaps", "ALL");
    indexAndBuild();

    //these match nothing
    assertTags(reqDoc(""));
    assertTags(reqDoc(" ") );
    assertTags(reqDoc("the") );

    //partial on N.London_Business_School matches nothing
    assertTags(reqDoc("school") );
    assertTags(reqDoc("a school") );
    assertTags(reqDoc("school a") );
    assertTags(reqDoc("school City") );

    String doc;

    doc = "school business london";//backwards
    assertTags(reqDoc(doc), tt(doc,"london", 0, N.London));

    //More interesting
    doc = "City of London Business School";
    assertTags(reqDoc(doc),
        tt(doc, "City of London", 0, N.City_of_London),
        tt(doc, "London", 0, N.London),
        tt(doc, "London Business School", 0, N.London_Business_School));

    doc = "City of London Business";
    assertTags(reqDoc(doc),
        tt(doc, "City of London", 0, N.City_of_London),
        tt(doc, "London", 0, N.London));
  }
  
  private TestTag tt(String doc, String substring, int substringIndex, N name) {
    assert substringIndex == 0;

    //little bit of copy-paste code from super.tt()
    int startOffset = -1, endOffset;
    int substringIndex1 = 0;
    for(int i = 0; i <= substringIndex1; i++) {
      startOffset = doc.indexOf(substring, ++startOffset);
      assert startOffset >= 0 : "The test itself is broken";
    }
    endOffset = startOffset+ substring.length();//1 greater (exclusive)
    return new TestTag(startOffset, endOffset, substring, lookupByName(name.getName()));
  }

}
