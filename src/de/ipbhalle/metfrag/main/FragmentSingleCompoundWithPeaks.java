/*
*
* Copyright (C) 2009-2010 IPB Halle, Sebastian Wolf
*
* Contact: swolf@ipb-halle.de
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program.  If not, see <http://www.gnu.org/licenses/>.
*
*/

package de.ipbhalle.metfrag.main;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import junit.framework.Assert;

import org.openscience.cdk.ChemFile;
import org.openscience.cdk.ChemObject;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.io.MDLReader;
import org.openscience.cdk.tools.CDKHydrogenAdder;
import org.openscience.cdk.tools.manipulator.AtomContainerManipulator;
import org.openscience.cdk.tools.manipulator.ChemFileManipulator;
import org.openscience.cdk.tools.manipulator.MolecularFormulaManipulator;

import de.ipbhalle.metfrag.fragmenter.Fragmenter;
import de.ipbhalle.metfrag.keggWebservice.KeggWebservice;
import de.ipbhalle.metfrag.massbankParser.Peak;
import de.ipbhalle.metfrag.read.Molfile;
import de.ipbhalle.metfrag.spectrum.AssignFragmentPeak;
import de.ipbhalle.metfrag.spectrum.CleanUpPeakList;
import de.ipbhalle.metfrag.spectrum.MatchedFragment;
import de.ipbhalle.metfrag.spectrum.WrapperSpectrum;
import de.ipbhalle.metfrag.tools.MolecularFormulaTools;
import de.ipbhalle.metfrag.tools.renderer.StructureRendererTable;


public class FragmentSingleCompoundWithPeaks {
	
	private List<File> l1 = null;
	WrapperSpectrum spectrum = null;
	double mzabs = 0.01;
	double mzppm = 50.0;
	IAtomContainer molecule = null;
	

	public FragmentSingleCompoundWithPeaks() {
		
		String candidate = "C00509";
		double exactMass = 272.06847;
		String peaks = "119.051 467.616 45\n" +
		   "123.044 370.662 36\n" +
		   "147.044 6078.145 606\n" +
		   "153.019 10000.0 999\n" +
		   "179.036 141.192 13\n" +
		   "189.058 176.358 16\n";
		int mode = 1;

		spectrum = new WrapperSpectrum(peaks, mode, exactMass);		
			
        //get mol file from kegg....remove "cpd:"
		String candidateMol = "";
				
		candidateMol = KeggWebservice.KEGGgetMol("C00509", "/vol/data/pathways/kegg/mol/");
		MDLReader reader;
		List<IAtomContainer> containersList;
		
        reader = new MDLReader(new StringReader(candidateMol));
        ChemFile chemFile = null;
		try {
			chemFile = (ChemFile)reader.read((ChemObject)new ChemFile());
		} catch (CDKException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}
        containersList = ChemFileManipulator.getAllAtomContainers(chemFile);
        molecule = containersList.get(0);
				
		
        //add hydrogens
        try {
			AtomContainerManipulator.percieveAtomTypesAndConfigureAtoms(molecule);
		} catch (CDKException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
        CDKHydrogenAdder hAdder = CDKHydrogenAdder.getInstance(molecule.getBuilder());
        try {
			hAdder.addImplicitHydrogens(molecule);
		} catch (CDKException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
        AtomContainerManipulator.convertImplicitToExplicitHydrogens(molecule);

		Double massDoubleOrig = MolecularFormulaTools.getMonoisotopicMass(MolecularFormulaManipulator.getMolecularFormula(molecule));
		massDoubleOrig = (double)Math.round((massDoubleOrig)*10000)/10000;
		String massOrig = massDoubleOrig.toString();
		
		        
        Fragmenter fragmenter = new Fragmenter((Vector<Peak>)spectrum.getPeakList().clone(), mzabs, mzppm, mode, true, true, true, false);
        try {
			l1 = fragmenter.generateFragmentsEfficient(molecule, true, 2, "C00509");
		} catch (CDKException e1) {
			e1.printStackTrace();
		} catch (Exception e1) {
			e1.printStackTrace();
		}
	}
	
	
	public void testFragmenterWithoutHydrogen() throws FileNotFoundException, CDKException
	{
		//get the original peak list again
		Vector<Peak> peakListParsed = spectrum.getPeakList();
		
		
		//clean up peak list
		CleanUpPeakList cList = new CleanUpPeakList((Vector<Peak>) peakListParsed.clone());
		Vector<Peak> cleanedPeakList = cList.getCleanedPeakList(spectrum.getExactMass());
		
		
		List<IAtomContainer> l = new ArrayList<IAtomContainer>();
		l = Molfile.ReadfolderTemp(l1);
		
		StructureRendererTable.Draw(molecule, l, "");
		
		//now find corresponding fragments to the mass
		AssignFragmentPeak afp = new AssignFragmentPeak(3);
		try {
			afp.assignFragmentPeak(l, cleanedPeakList, mzabs, mzppm, spectrum.getMode(), false);
		} catch (CDKException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		Vector<MatchedFragment> hits = afp.getHits();
		Vector<Double> peaksFound = new Vector<Double>();
		
		//get all the identified peaks
		for (int i = 0; i < hits.size(); i++) {
			peaksFound.add(hits.get(i).getPeak().getMass());
		}
		
		StructureRendererTable.DrawHits(molecule, hits, "");
	}
	
	
	public static void main(String[] args) {
		FragmentSingleCompoundWithPeaks test = new FragmentSingleCompoundWithPeaks();
		try {
			test.testFragmenterWithoutHydrogen();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (CDKException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}