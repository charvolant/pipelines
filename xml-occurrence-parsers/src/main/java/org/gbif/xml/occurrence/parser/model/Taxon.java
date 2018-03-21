package org.gbif.xml.occurrence.parser.model;

import org.gbif.xml.occurrence.parser.constants.TaxonRankEnum;

public class Taxon {

  private TaxonRankEnum rank;
  private String name;

  public Taxon() {
  }

  public Taxon(TaxonRankEnum rank, String name) {
    this.rank = rank;
    this.name = name;
  }

  public TaxonRankEnum getRank() {
    return rank;
  }

  public void setRank(TaxonRankEnum rank) {
    this.rank = rank;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

}