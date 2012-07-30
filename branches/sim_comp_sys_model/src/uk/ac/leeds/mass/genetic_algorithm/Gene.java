/*
©Copyright 2012 Nick Malleson
This file is part of RepastCity.

RepastCity is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

RepastCity is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with RepastCity.  If not, see <http://www.gnu.org/licenses/>.
*/
package uk.ac.leeds.mass.genetic_algorithm;

import java.io.Serializable;

/**Class to encapsulate a gene in a genetic algorithm. A number of genes are combined to form a
 * chromosome.
 *
 *
 * @author Nick Malleson
 * @see Genetic
 * @see Chromosome
 */

public class Gene implements Serializable {


	private static final long serialVersionUID = 5109981620051693659L;
	
private String name;
  private double min;		// The min and max values this Gene is allowed to take
  private double max;
  private double mutate;	// The maximum amount this Gene is allowed to mutate by
  private double value;		// The actual value of this gene

  /**Constructs a Gene with all required parameters.
   *
   * @param min the minimum value this Gene is allowed to take.
   * @param max the maximum value this Gene is allowed to take.
   * @param mutate the maximum amount this Gene is allowed to mutate by.
   */

  public Gene(String name, double min, double max, double mutate)
  {
    this.name = name;
    this.min = min;
    this.max = max;
    this.mutate = mutate;
  }

  /**
   * Creat a new gene with the same parameters as the input gene.
   */
   public Gene(Gene gene) {
      this.name = gene.name;
      this.min = gene.min;
      this.max = gene.max;
      this.mutate = gene.mutate;
   }

  // ***************************** GET / SET METHODS *******************************

  /**Gets the value of this Gene.
   *
   * @return the current actual value of this Gene.
   */

  public double getValue()
  {
    return this.value;
  }

  /**Set the value of this gene.
   *
   * @param value The new value of this Gene. It must be between the min and max boundaries.
   * @return A boolean to indicate success or failure. If the new value is not between the min and
   * max values for this Gene then the Gene is not altered and false is returned.
   */

  public boolean setValue(double value)
  {
    if ( (value <= this.max) && (value >= this.min) ) {
      this.value = value;
      return true;
    }
    else
      return false;
  }

  /**Gets the min value of this Gene.
   *
   * @return the minimum value this Gene is allowed to take.
   */

  public double getMinValue()
  {
    return this.min;
  }

  /**Gets the max value of this Gene.
   *
   * @return the maximum value this Gene is allowed to take
   */

  public double getMaxValue()
  {
    return this.max;
  }

  /**Gets the mutate value associated with this Gene
   *
   * @return the mutate value, the maximum amount this Gene is allowed to be mutated by.
   */

  public double getMutate()
  {
    return this.mutate;
  }

  /**Sets the mutate value.
   *
   * @param mutate the new value of the mutute variable.
   */

  public void setMutate(double mutate)
  {
    this.mutate = mutate;
  }

  /**Gets the name of this Gene.
   *
   * @return the name of this Gene as a String.
   */

  public String getName()
  {
    return this.name;
  }

  public boolean equals(Object obj) {
      if (!(obj instanceof Gene)) {
            return false;
        }
      Gene g = (Gene) obj;
      return this.getValue() == g.getValue() ;
  }

}
