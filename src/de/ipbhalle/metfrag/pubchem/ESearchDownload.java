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
package de.ipbhalle.metfrag.pubchem;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.xml.rpc.ServiceException;

import org.openscience.cdk.ChemFile;
import org.openscience.cdk.formula.MolecularFormula;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.io.MDLV2000Reader;
import org.openscience.cdk.io.SDFWriter;
import org.openscience.cdk.smiles.SmilesGenerator;
import org.openscience.cdk.tools.manipulator.MolecularFormulaManipulator;

import de.ipbhalle.metfrag.read.Molfile;
import de.ipbhalle.metfrag.read.SDFFile;
import de.ipbhalle.metfrag.tools.MolecularFormulaTools;

import gov.nih.nlm.ncbi.pubchem.CompressType;
import gov.nih.nlm.ncbi.pubchem.EntrezKey;
import gov.nih.nlm.ncbi.pubchem.FormatType;
import gov.nih.nlm.ncbi.pubchem.PUGLocator;
import gov.nih.nlm.ncbi.pubchem.PUGSoap;
import gov.nih.nlm.ncbi.pubchem.StatusType;
import gov.nih.nlm.ncbi.www.soap.eutils.EUtilsServiceLocator;
import gov.nih.nlm.ncbi.www.soap.eutils.EUtilsServiceSoap;
import gov.nih.nlm.ncbi.www.soap.eutils.esearch.ESearchRequest;
import gov.nih.nlm.ncbi.www.soap.eutils.esearch.ESearchResult;

public class ESearchDownload {    
	
	/**
	 * Esearch download with exact mass for compounds only until 2006/02/06.
	 * 
	 * @param exactMass the exact mass
	 * @param error the error
	 * 
	 * @return the vector< string>
	 * 
	 * @throws Exception the exception
	 */
	public static Map<String, String> ESearchDownloadExactMassFebruary2006(double lowerBound, double upperBound) throws Exception
	{
		Vector<String> foundIds = new Vector<String>();
		
		Map<String, String> idToFormula = new HashMap<String, String>();
	
        EUtilsServiceLocator eutils_locator = new EUtilsServiceLocator();
        EUtilsServiceSoap eutils_soap = eutils_locator.geteUtilsServiceSoap();
		PUGLocator pug_locator = new PUGLocator();
		PUGSoap pug_soap = pug_locator.getPUGSoap();
        
        // search "aspirin" in PubChem Compound
        ESearchRequest request = new ESearchRequest();
        String db = new String("pccompound");
        request.setDb(db);
		//932[uid] AND ((-2147483648 : 2008/02/28[CreateDate]))
		request.setTerm(lowerBound + ":" + upperBound + "[EMAS]" + " AND ((-2147483648 : 2006/02/06[CreateDate])");
        // create a history item, and don't return any actual ids in the
        // SOAP response
        request.setUsehistory("y");
        request.setRetMax("0");
        ESearchResult result = eutils_soap.run_eSearch(request);
        if (result.getQueryKey() == null || result.getQueryKey().length() == 0 ||
            result.getWebEnv() == null || result.getWebEnv().length() == 0)
        {
            throw new Exception("ESearch failed to return query_key and WebEnv");
        }
        System.out.println("ESearch returned " + result.getCount() + " hits");
        
        // give this Entrez History info to PUG SOAP
        EntrezKey entrezKey = new EntrezKey(db, result.getQueryKey(), result.getWebEnv());
        String listKey = pug_soap.inputEntrez(entrezKey);
        System.out.println("ListKey = " + listKey);

        // Initialize the download; request SDF with gzip compression
		String downloadKey = pug_soap.download(listKey, FormatType.eFormat_SDF, CompressType.eCompress_None, false);
		System.out.println("DownloadKey = " + downloadKey);
	        
	        // Wait for the download to be prepared
		StatusType status;
		while ((status = pug_soap.getOperationStatus(downloadKey)) 
	                    == StatusType.eStatus_Running || 
	               status == StatusType.eStatus_Queued) 
	        {
	            System.out.println("Waiting for download to finish...");
		    Thread.sleep(10000);
		}
        
        // On success, get the download URL, save to local file
        if (status == StatusType.eStatus_Success) {
        	
        	// PROXY
		    System.getProperties().put( "ftp.proxySet", "true" );
		    System.getProperties().put( "ftp.proxyHost", "www.ipb-halle.de" );
		    System.getProperties().put( "ftp.proxyPort", "3128" );
        	
        	
		    URL url = null;
		    InputStream input = null;
		    
            try
            {
            	url = new URL(pug_soap.getDownloadUrl(downloadKey));
                System.out.println("Success! Download URL = " + url.toString());
                
                // get input stream from URL
                URLConnection fetch = url.openConnection();
            	input = fetch.getInputStream();
            }
            catch(IOException e)
            {
            	System.out.println("Error downloading! " + e.getMessage());
            	//try again!
            	ESearchDownloadExactMassFebruary2006(lowerBound, upperBound);
            }
            
            // open local file based on the URL file name
            String filename = "/tmp"
                    + url.getFile().substring(url.getFile().lastIndexOf('/'));
            FileOutputStream output = new FileOutputStream(filename);
            System.out.println("Writing data to " + filename);
            
            // buffered read/write
            byte[] buffer = new byte[10000];
            int n;
            while ((n = input.read(buffer)) > 0)
                output.write(buffer, 0, n);
            
            //now read in the file
			FileInputStream in = null;
	        in = new FileInputStream(filename);
            
            MDLV2000Reader reader = new MDLV2000Reader(in);
	        ChemFile fileContents = (ChemFile)reader.read(new ChemFile());
	        System.out.println("Got " + fileContents.getChemSequence(0).getChemModelCount() + " atom containers");
	        
	        for (int i = 0; i < fileContents.getChemSequence(0).getChemModelCount(); i++) {
				Map<Object, Object> properties = fileContents.getChemSequence(0).getChemModel(i).getMoleculeSet().getAtomContainer(0).getProperties();
				IAtomContainer mol = fileContents.getChemSequence(0).getChemModel(i).getMoleculeSet().getAtomContainer(0);
				String molFormula = MolecularFormulaManipulator.getString(MolecularFormulaManipulator.getMolecularFormula(mol, new MolecularFormula()));
		        System.out.println((String) properties.get("PUBCHEM_COMPOUND_CID"));
		        foundIds.add(properties.get("PUBCHEM_COMPOUND_CID").toString());
		        idToFormula.put(properties.get("PUBCHEM_COMPOUND_CID").toString(), molFormula);
			}

	        System.out.println("Read the file");
	        
	        new File("/tmp"	+ url.getFile().substring(url.getFile().lastIndexOf('/'))).delete();
	        
	        System.out.println("Temp file deleted!");
            
        } else {
            System.out.println("Error: " 
                + pug_soap.getStatusMessage(downloadKey));            
        }
        
        
        return idToFormula;
    }
	
	
	/**
	 * Esearch download with exact mass for compounds only until 2006/02/06.
	 * 
	 * @param exactMass the exact mass
	 * @param error the error
	 * 
	 * @return the vector< string>
	 * 
	 * @throws Exception the exception
	 */
	public static Map<String, String> ESearchDownloadExactMassFebruary2006(double lowerBound, double upperBound, String folder) throws Exception
	{
		Vector<String> foundIds = new Vector<String>();
		
		Map<String, String> idToFormula = new HashMap<String, String>();
	
		if(new File(folder).exists() && new File(folder).listFiles().length > 0)
		{
			List<IAtomContainer> mols = Molfile.Readfolder(folder + "/");
			for (IAtomContainer mol : mols) {
				System.out.println((String) mol.getProperty("PUBCHEM_COMPOUND_CID"));
		        foundIds.add(mol.getProperty("PUBCHEM_COMPOUND_CID").toString());
		        String molFormula = MolecularFormulaManipulator.getString(MolecularFormulaManipulator.getMolecularFormula(mol, new MolecularFormula()));
		        idToFormula.put(mol.getProperty("PUBCHEM_COMPOUND_CID").toString(), molFormula);
			}
			
			return idToFormula;
	        
		}
		else
		{
			EUtilsServiceLocator eutils_locator = new EUtilsServiceLocator();
	        EUtilsServiceSoap eutils_soap = eutils_locator.geteUtilsServiceSoap();
			PUGLocator pug_locator = new PUGLocator();
			PUGSoap pug_soap = pug_locator.getPUGSoap();
	        
	        // search "aspirin" in PubChem Compound
	        ESearchRequest request = new ESearchRequest();
	        String db = new String("pccompound");
	        request.setDb(db);
			//932[uid] AND ((-2147483648 : 2008/02/28[CreateDate]))
			request.setTerm(lowerBound + ":" + upperBound + "[EMAS]" + " AND ((-2147483648 : 2006/02/06[CreateDate])");
	        // create a history item, and don't return any actual ids in the
	        // SOAP response
	        request.setUsehistory("y");
	        request.setRetMax("0");
	        ESearchResult result = eutils_soap.run_eSearch(request);
	        if (result.getQueryKey() == null || result.getQueryKey().length() == 0 ||
	            result.getWebEnv() == null || result.getWebEnv().length() == 0)
	        {
	            throw new Exception("ESearch failed to return query_key and WebEnv");
	        }
	        System.out.println("ESearch returned " + result.getCount() + " hits");
	        
	        // give this Entrez History info to PUG SOAP
	        EntrezKey entrezKey = new EntrezKey(db, result.getQueryKey(), result.getWebEnv());
	        String listKey = pug_soap.inputEntrez(entrezKey);
	        System.out.println("ListKey = " + listKey);

	        // Initialize the download; request SDF with gzip compression
			String downloadKey = pug_soap.download(listKey, FormatType.eFormat_SDF, CompressType.eCompress_None, false);
			System.out.println("DownloadKey = " + downloadKey);
		        
		        // Wait for the download to be prepared
			StatusType status;
			while ((status = pug_soap.getOperationStatus(downloadKey)) 
		                    == StatusType.eStatus_Running || 
		               status == StatusType.eStatus_Queued) 
		        {
		            System.out.println("Waiting for download to finish...");
			    Thread.sleep(10000);
			}
	        
	        // On success, get the download URL, save to local file
	        if (status == StatusType.eStatus_Success) {
	        	
	        	// PROXY
			    System.getProperties().put( "ftp.proxySet", "true" );
			    System.getProperties().put( "ftp.proxyHost", "www.ipb-halle.de" );
			    System.getProperties().put( "ftp.proxyPort", "3128" );
	        	
	        	
			    URL url = null;
			    InputStream input = null;
			    
	            try
	            {
	            	url = new URL(pug_soap.getDownloadUrl(downloadKey));
	                System.out.println("Success! Download URL = " + url.toString());
	                
	                // get input stream from URL
	                URLConnection fetch = url.openConnection();
	            	input = fetch.getInputStream();
	            }
	            catch(IOException e)
	            {
	            	System.out.println("Error downloading! " + e.getMessage());
	            	//try again!
	            	ESearchDownloadExactMassFebruary2006(lowerBound, upperBound);
	            }
	            
	            // open local file based on the URL file name
	            String filename = "/tmp"
	                    + url.getFile().substring(url.getFile().lastIndexOf('/'));
	            FileOutputStream output = new FileOutputStream(filename);
	            System.out.println("Writing data to " + filename);
	            
	            // buffered read/write
	            byte[] buffer = new byte[10000];
	            int n;
	            while ((n = input.read(buffer)) > 0)
	                output.write(buffer, 0, n);
	            
	            //now read in the file
				FileInputStream in = null;
		        in = new FileInputStream(filename);
	            
	            MDLV2000Reader reader = new MDLV2000Reader(in);
		        ChemFile fileContents = (ChemFile)reader.read(new ChemFile());
		        System.out.println("Got " + fileContents.getChemSequence(0).getChemModelCount() + " atom containers");
		        
		        for (int i = 0; i < fileContents.getChemSequence(0).getChemModelCount(); i++) {
					Map<Object, Object> properties = fileContents.getChemSequence(0).getChemModel(i).getMoleculeSet().getAtomContainer(0).getProperties();
					
					IAtomContainer mol = fileContents.getChemSequence(0).getChemModel(i).getMoleculeSet().getAtomContainer(0);
					String molFormula = MolecularFormulaManipulator.getString(MolecularFormulaManipulator.getMolecularFormula(mol, new MolecularFormula()));
			        System.out.println((String) properties.get("PUBCHEM_COMPOUND_CID"));
			        
			        new File(folder + "/").mkdirs();
			        SDFWriter sdfWriter = new SDFWriter(new FileOutputStream(new File(folder + "/" + (String) properties.get("PUBCHEM_COMPOUND_CID"))));
			        sdfWriter.write(fileContents.getChemSequence(0).getChemModel(i).getMoleculeSet().getAtomContainer(0));
			        sdfWriter.close();
			        
			        foundIds.add(properties.get("PUBCHEM_COMPOUND_CID").toString());
			        idToFormula.put(properties.get("PUBCHEM_COMPOUND_CID").toString(), molFormula);
				}

		        System.out.println("Read the file");
		        
		        new File("/tmp"	+ url.getFile().substring(url.getFile().lastIndexOf('/'))).delete();
		        
		        System.out.println("Temp file deleted!");
	            
	        } else {
	            System.out.println("Error: " 
	                + pug_soap.getStatusMessage(downloadKey));            
	        }
	        
	        
	        return idToFormula;
		}
    }
	
	
	/**
	 * Esearch download with exact mass for compounds only until 2006/02/06.
	 * 
	 * @param exactMass the exact mass
	 * @param error the error
	 * 
	 * @return the vector< string>
	 * 
	 * @throws Exception the exception
	 */
	public static Map<String, IAtomContainer> ESearchDownloadPubChemIDs(List<String> idList) throws Exception
	{
		Map<String, IAtomContainer> hits = new HashMap<String, IAtomContainer>();
	
        EUtilsServiceLocator eutils_locator = new EUtilsServiceLocator();
        EUtilsServiceSoap eutils_soap = eutils_locator.geteUtilsServiceSoap();
		PUGLocator pug_locator = new PUGLocator();
		PUGSoap pug_soap = pug_locator.getPUGSoap();
        
        // search "aspirin" in PubChem Compound
        ESearchRequest request = new ESearchRequest();
        String db = new String("pccompound");
        request.setDb(db);
        
        //build request string
        String query = "";
        int count = 0;
        for (String string : idList) {
        	if(count == idList.size() - 1)
        		query += string + "[uid]";
        	else
        		query += string + "[uid] or ";
        	count++;
		}
		request.setTerm(query);
        // create a history item, and don't return any actual ids in the
        // SOAP response
        request.setUsehistory("y");
        request.setRetMax("0");
        ESearchResult result = eutils_soap.run_eSearch(request);
        if (result.getQueryKey() == null || result.getQueryKey().length() == 0 ||
            result.getWebEnv() == null || result.getWebEnv().length() == 0)
        {
            throw new Exception("ESearch failed to return query_key and WebEnv");
        }
        System.out.println("ESearch returned " + result.getCount() + " hits");
        
        // give this Entrez History info to PUG SOAP
        EntrezKey entrezKey = new EntrezKey(db, result.getQueryKey(), result.getWebEnv());
        String listKey = pug_soap.inputEntrez(entrezKey);
        System.out.println("ListKey = " + listKey);

        // Initialize the download; request SDF with gzip compression
		String downloadKey = pug_soap.download(listKey, FormatType.eFormat_SDF, CompressType.eCompress_None, false);
		System.out.println("DownloadKey = " + downloadKey);
	        
	        // Wait for the download to be prepared
		StatusType status;
		while ((status = pug_soap.getOperationStatus(downloadKey)) 
	                    == StatusType.eStatus_Running || 
	               status == StatusType.eStatus_Queued) 
	        {
	            System.out.println("Waiting for download to finish...");
		    Thread.sleep(10000);
		}
        
        // On success, get the download URL, save to local file
        if (status == StatusType.eStatus_Success) {
        	
        	// PROXY
		    System.getProperties().put( "ftp.proxySet", "true" );
		    System.getProperties().put( "ftp.proxyHost", "www.ipb-halle.de" );
		    System.getProperties().put( "ftp.proxyPort", "3128" );
        	
        	
		    URL url = null;
		    InputStream input = null;
		    
            try
            {
            	url = new URL(pug_soap.getDownloadUrl(downloadKey));
                System.out.println("Success! Download URL = " + url.toString());
                
                // get input stream from URL
                URLConnection fetch = url.openConnection();
            	input = fetch.getInputStream();
            }
            catch(IOException e)
            {
            	System.out.println("Error downloading! " + e.getMessage());
            	//try again!
            }
            
            // open local file based on the URL file name
            String filename = "/tmp"
                    + url.getFile().substring(url.getFile().lastIndexOf('/'));
            FileOutputStream output = new FileOutputStream(filename);
            System.out.println("Writing data to " + filename);
            
            // buffered read/write
            byte[] buffer = new byte[10000];
            int n;
            while ((n = input.read(buffer)) > 0)
                output.write(buffer, 0, n);
            
            //now read in the file
			FileInputStream in = null;
	        in = new FileInputStream(filename);
            
            MDLV2000Reader reader = new MDLV2000Reader(in);
	        ChemFile fileContents = (ChemFile)reader.read(new ChemFile());
	        System.out.println("Got " + fileContents.getChemSequence(0).getChemModelCount() + " atom containers");
	        
	        for (int i = 0; i < fileContents.getChemSequence(0).getChemModelCount(); i++) {
				Map<Object, Object> properties = fileContents.getChemSequence(0).getChemModel(i).getMoleculeSet().getAtomContainer(0).getProperties();
		        System.out.println((String) properties.get("PUBCHEM_COMPOUND_CID"));
		        hits.put(properties.get("PUBCHEM_COMPOUND_CID").toString(), fileContents.getChemSequence(0).getChemModel(i).getMoleculeSet().getAtomContainer(0));
			}

	        System.out.println("Read the file");
	        
	        new File("/tmp"	+ url.getFile().substring(url.getFile().lastIndexOf('/'))).delete();
	        
	        System.out.println("Temp file deleted!");
            
        } else {
            System.out.println("Error: " 
                + pug_soap.getStatusMessage(downloadKey));            
        }
        
        
        return hits;
    }

}
