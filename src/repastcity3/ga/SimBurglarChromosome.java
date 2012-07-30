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

package repastcity3.ga;

import java.io.Serializable;

import uk.ac.leeds.mass.genetic_algorithm.Chromosome;
import uk.ac.leeds.mass.genetic_algorithm.GAException;
import uk.ac.leeds.mass.genetic_algorithm.Gene;

/**
 * 
 * @author Nick Malleson
 *
 */
public class SimBurglarChromosome extends Chromosome implements Serializable {

	private static final long serialVersionUID = 1L;

/**
    * This demo class uses four Genes, each of which can vary between 0 and 10.
    * @return
    */
   @Override
   public Gene[] createGenes() {
//      Gene[] thegenes = new Gene[4];
//      for (int i = 0; i < 4; i++) {
//         thegenes[i] = new Gene("Gene" + i, 0.0, 10.0, 1.0);
//      }
//      return thegenes;
      
	   throw new RuntimeException("XXXX method not implemented");
   }

   /**
    * In this demo application the fitness is calculated such that the best result
    * can be obtained when all genes (a,b,c,d) are plugged into the following function
    * y=a^4+b^3+c^2+d and the result is about 170. (E.g. 2.5^4+4.0^3+8^2+3.5=170).
    *
    * @throws GAException
    * @param info The GAInformation object which provides information about the
    * caller, ignored here.
    * @see GAInformation
    */
   @Override
   public void calcFitness() throws GAException {
	   
	   throw new RuntimeException("XXXX method not implemented");
	   
//      this.fitness = fit;
   }


}
