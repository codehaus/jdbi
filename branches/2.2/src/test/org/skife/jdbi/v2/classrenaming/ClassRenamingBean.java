package org.skife.jdbi.v2.classrenaming;

public class ClassRenamingBean {

	private String bar;
	private String baz;
	private Integer blo;
	public String getBar() {
		return bar;
	}
	public void setBar(String bar) {
		this.bar = bar;
	}
	public String getBaz() {
		return baz;
	}
	public void setBaz(String baz) {
		this.baz = baz;
	}
	public Integer getBlo() {
		return blo;
	}
	public void setBlo(Integer blo) {
		this.blo = blo;
	}

	public String toString() {
		return "Bar: " + bar + "\nBaz: " + baz + "\nBlo: " + blo + "\n";
	}
}
