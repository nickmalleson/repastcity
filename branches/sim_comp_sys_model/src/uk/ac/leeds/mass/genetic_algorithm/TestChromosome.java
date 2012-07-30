package uk.ac.leeds.mass.genetic_algorithm;

import java.io.Serializable;

/**
 * An example <code>Chromosome</code> class.
 * @author Nick Malleson
 */
public class TestChromosome extends Chromosome implements Serializable {


	private static final long serialVersionUID = 2350837145617173078L;

	/**
	 * This demo class uses four Genes, each of which can vary between 0 and 10.
	 * @return
	 */
	@Override
	public Gene[] createGenes() {
		Gene[] thegenes = new Gene[4];
		for (int i = 0; i < 4; i++) {
			thegenes[i] = new Gene("Gene" + i, 0.0, 10.0, 1.0);
		}
		return thegenes;
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
		//      double total = 0.0;
		//      for (int i=0; i<this.genes.length; i++) {
		//         total += genes[i].getValue();
		//      }
		double y = Math.pow(genes[0].getValue(), 4)+Math.pow(genes[1].getValue(), 3)+Math.pow(genes[2].getValue(),2)+genes[1].getValue();
		double fit = 170 - y;
		if (fit<0)
			fit = fit*-1;

		this.fitness = fit;
	}

}
