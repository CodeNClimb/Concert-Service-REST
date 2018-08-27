package nz.ac.auckland.concert.common.types;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;

/**
 * Enumerated type for classifying seats according to price bands.
 *
 */
@XmlType(name = "price-band-type")
@XmlEnum
public enum PriceBand {
	@XmlEnumValue(value = "price-band-A")
	PriceBandA,

	@XmlEnumValue(value = "price-band-B")
	PriceBandB,

	@XmlEnumValue(value = "price-band-C")
	PriceBandC
}
