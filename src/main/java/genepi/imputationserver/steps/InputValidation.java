package genepi.imputationserver.steps;

import genepi.hadoop.HdfsUtil;
import genepi.imputationserver.steps.vcf.VcfFile;
import genepi.imputationserver.steps.vcf.VcfFileUtil;
import genepi.io.FileUtil;

import java.io.IOException;
import java.util.List;
import java.util.Vector;

import cloudgene.mapred.jobs.CloudgeneContext;
import cloudgene.mapred.jobs.CloudgeneStep;
import cloudgene.mapred.jobs.Message;
import cloudgene.mapred.steps.importer.IImporter;
import cloudgene.mapred.steps.importer.ImporterFactory;
import cloudgene.mapred.wdl.WdlStep;

public class InputValidation extends CloudgeneStep {

	@Override
	public boolean run(WdlStep step, CloudgeneContext context) {

		boolean importOk = importVcfFiles(context);
		if (!importOk) {
			return false;
		}

		return checkVcfFiles(context);

	}

	private boolean checkVcfFiles(CloudgeneContext context) {

		String folder = getFolder(InputValidation.class);
		String tabix = FileUtil.path(folder, "bin", "tabix");

		String inputFiles = context.get("files");
		int chunkSize = Integer.parseInt(context.get("chunksize"));

		String files = FileUtil.path(context.getLocalTemp(), "input");

		// exports files from hdfs
		try {

			HdfsUtil.getFolder(inputFiles, files);

		} catch (Exception e) {
			error("Downloading files: " + e.getMessage());
			return false;

		}

		List<VcfFile> validVcfFiles = new Vector<VcfFile>();

		Message analyzeMessage = createLogMessage("Analyze file ",
				Message.RUNNING);
		List<String> chromosomes = new Vector<String>();

		int chunks = 0;
		int noSnps = 0;
		int noSamples = 0;
		int noChromosomes = 0;

		boolean phased = true;

		String[] vcfFiles = FileUtil.getFiles(files, "*.vcf.gz$|*.vcf$");

		if (vcfFiles.length == 0) {
			analyzeMessage.setType(Message.ERROR);
			analyzeMessage.setMessage("No valid vcf files found.");
			return false;
		}

		Message chromosomeMessage = createLogMessage("", Message.OK);

		for (String filename : vcfFiles) {
			analyzeMessage.setMessage("Analyze file "
					+ FileUtil.getFilename(filename) + "...");

			try {

				VcfFile vcfFile = VcfFileUtil.load(filename, chunkSize, tabix);

				if (VcfFileUtil.isAutosomal(vcfFile.getChromosome())) {

					validVcfFiles.add(vcfFile);
					chromosomes.add(vcfFile.getChromosome());
					String chromosomeString = "";
					for (String chr : chromosomes) {
						chromosomeString += " " + chr;
						noChromosomes++;
					}
					noSamples = vcfFile.getNoSamples();
					noSnps += vcfFile.getNoSnps();
					chunks += vcfFile.getChunks().size();

					phased = phased && vcfFile.isPhased();

					chromosomeMessage.setMessage("Samples: " + noSamples + "\n"
							+ "Chromosomes:" + chromosomeString + "\n"
							+ "SNPs: " + noSnps + "\n" + "Chunks: " + chunks
							+ "\n" + "Datatype: "
							+ (phased ? "phased" : "unphased") + "\n"
							+ "Genome Build: 37");

				}

			} catch (IOException e) {

				analyzeMessage.setType(Message.ERROR);
				chromosomeMessage.setType(Message.ERROR);
				chromosomeMessage.setMessage(e.getMessage());
				return false;

			}

		}

		if (validVcfFiles.size() > 0) {

			analyzeMessage.setType(Message.OK);
			analyzeMessage.setMessage(validVcfFiles.size()
					+ " valid VCF file(s) found.");
			chromosomeMessage.setType(Message.OK);

			// init counteres
			context.incCounter("samples", noSamples);
			context.incCounter("genotypes", noSamples * noSnps);
			context.incCounter("chromosomes", noSamples * noChromosomes);
			context.incCounter("runs", 1);

			return true;

		} else {

			analyzeMessage.setType(Message.ERROR);
			analyzeMessage.setMessage(validVcfFiles.size()
					+ " valid VCF file(s) found.");
			chromosomeMessage.setType(Message.ERROR);

			return false;
		}
	}

	private boolean importVcfFiles(CloudgeneContext context) {
		for (String input : context.getInputs()) {

			if (ImporterFactory.needsImport(context.get(input))) {

				String[] urlList = context.get(input).split(";")[0]
						.split("\\s+");

				String username = "";
				if (context.get(input).split(";").length > 1) {
					username = context.get(input).split(";")[1];
				}

				String password = "";
				if (context.get(input).split(";").length > 2) {
					password = context.get(input).split(";")[2];
				}

				for (String url2 : urlList) {

					String url = url2 + ";" + username + ";" + password;

					String target = HdfsUtil.path(context.getHdfsTemp(),
							"importer", input);

					try {

						beginTask("Import File(s) " + url2 + "...");

						IImporter importer = ImporterFactory.createImporter(
								url, target);

						if (importer != null) {

							boolean successful = importer.importFiles();

							if (successful) {

								context.setInput(input, target);

								endTask("Import File(s) " + url2
										+ " successful.", Message.OK);

							} else {

								endTask("Import File(s) " + url2 + " failed: "
										+ importer.getErrorMessage(),
										Message.ERROR);

								return false;

							}

						} else {

							endTask("Import File(s) " + url2
									+ " failed: Protocol not supported",
									Message.ERROR);

							return false;

						}

					} catch (Exception e) {
						endTask("Import File(s) " + url2 + " failed: "
								+ e.toString(), Message.ERROR);
						return false;
					}

				}

			}
		}

		return true;

	}

}
