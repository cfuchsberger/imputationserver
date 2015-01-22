package genepi.imputationserver.steps.imputationMinimac3;

import genepi.hadoop.command.Command;
import genepi.imputationserver.steps.vcf.VcfChunk;
import genepi.imputationserver.steps.vcf.VcfChunkOutput;
import genepi.io.FileUtil;
import genepi.io.plink.MapFileReader;
import genepi.io.plink.Snp;
import genepi.io.text.LineReader;
import genepi.io.text.LineWriter;

import java.io.File;
import java.io.IOException;

public class ImputationPipelineMinimac3 {

	private String minimacCommand;
	private String hapiUrCommand;
	private String hapiUrPreprocessCommand;
	private String shapeItCommand;
	private String vcfCookerCommand;
	private String vcf2HapCommand;
	private String refPanelFilename;
	private int minimacWindow;
	private int phasingWindow;
	private int rounds;
	private String mapFilename;

	public void init() {

	}

	public void setMinimacCommand(String minimacCommand) {
		this.minimacCommand = minimacCommand;
	}

	public void setHapiUrCommand(String hapiUrCommand) {
		this.hapiUrCommand = hapiUrCommand;
	}

	public void setVcfCookerCommand(String vcfCookerCommand) {
		this.vcfCookerCommand = vcfCookerCommand;
	}

	public void setVcf2HapCommand(String vcf2HapCommand) {
		this.vcf2HapCommand = vcf2HapCommand;
	}

	public void setShapeItCommand(String shapeItCommand) {
		this.shapeItCommand = shapeItCommand;
	}

	public void setMinimacWindow(int minimacWindow) {
		this.minimacWindow = minimacWindow;
	}

	public void setPhasingWindow(int phasingWindow) {
		this.phasingWindow = phasingWindow;
	}

	public void setReferencePanel(String refPanelFilename) {
		this.refPanelFilename = refPanelFilename;
	}

	public int getRounds() {
		return rounds;
	}

	public void setRounds(int rounds) {
		this.rounds = rounds;
	}

	public boolean vcfToBed(VcfChunkOutput output) {

		Command vcfCooker = new Command(vcfCookerCommand);
		vcfCooker.setSilent(false);

		vcfCooker.setParams("--in-vcf", output.getVcfFilename(), "--write-bed",
				"--out", output.getPrefix());
		vcfCooker.saveStdOut(output.getPrefix() + ".vcfcooker.out");
		vcfCooker.saveStdErr(output.getPrefix() + ".vcfcooker.err");
		System.out.println("Command: " + vcfCooker.getExecutedCommand());
		return (vcfCooker.execute() == 0);

	}

	public boolean vcfToHap2(VcfChunkOutput output) {
		Command vcf2Hap = new Command(vcf2HapCommand);
		vcf2Hap.setSilent(false);
		vcf2Hap.setParams("--in-vcf", output.getVcfFilename(), "--out",
				output.getPrefix());
		vcf2Hap.saveStdOut(output.getPrefix() + ".vcf2hap.out");
		vcf2Hap.saveStdErr(output.getPrefix() + ".vcf2hap.err");
		return (vcf2Hap.execute() == 0);
	}

	public int getNoSnps(VcfChunk input, VcfChunkOutput output) {
		MapFileReader reader = null;

		try {
			System.out.println(FileUtil.getLineCount(output.getBimFilename()));
		} catch (IOException e1) {
			System.out.println(e1.getMessage());
			e1.printStackTrace();
		}

		int noSnps = 0;
		try {
			reader = new MapFileReader(output.getBimFilename());
			while (reader.next()) {
				Snp snp = reader.get();
				System.out.println(snp.getPhysicalPosition());
				if (snp.getPhysicalPosition() >= input.getStart()
						&& snp.getPhysicalPosition() <= input.getEnd()) {
					noSnps++;
				}
			}
			reader.close();
		} catch (Exception e) {
			System.out.println("Error during snp count: " + e.getMessage());
			e.printStackTrace();
			return -1;
		}
		return noSnps;
	}

	public boolean phaseWithHapiUr(VcfChunk input, VcfChunkOutput output) {

		// +/- 1 Mbases
		int start = input.getStart() - phasingWindow;
		if (start < 1) {
			start = 1;
		}

		int end = input.getEnd() + phasingWindow;

		String bimWthMap = output.getPrefix() + ".map.bim";

		Command hapiUrPre = new Command(hapiUrPreprocessCommand);
		hapiUrPre.setParams(output.getBimFilename(), mapFilename);
		hapiUrPre.saveStdOut(bimWthMap);
		hapiUrPre.setSilent(true);
		hapiUrPre.execute();
		System.out.println("Command: " + hapiUrPre.getExecutedCommand());

		Command hapiUr = new Command(hapiUrCommand);
		hapiUr.setSilent(false);

		hapiUr.setParams("-g", output.getBedFilename(), "-s", bimWthMap, "-i",
				output.getFamFilename(), "-w", "73", "-o", output.getPrefix(),
				"-c", input.getChromosome(), "--start", start + "", "--end",
				end + "", "--impute2");
		hapiUr.saveStdOut(output.getPrefix() + ".hapiur.out");
		hapiUr.saveStdErr(output.getPrefix() + ".hapiur.err");
		System.out.println("Command: " + hapiUr.getExecutedCommand());
		hapiUr.execute();

		// haps to vcf
		Command shapeItConvert = new Command(shapeItCommand);
		shapeItConvert.setSilent(false);
		shapeItConvert.setParams("-convert", "--input-haps",
				output.getPrefix(), "--output-vcf", output.getVcfFilename());
		System.out.println("Command: " + shapeItConvert.getExecutedCommand());

		return (shapeItConvert.execute() == 0);
	}

	public boolean phaseWithShapeIt(VcfChunk input, VcfChunkOutput output) {

		// +/- 1 Mbases
		int start = input.getStart() - phasingWindow;
		if (start < 1) {
			start = 1;
		}

		int end = input.getEnd() + phasingWindow;

		Command shapeIt = new Command(shapeItCommand);
		shapeIt.setSilent(false);

		shapeIt.setParams("--input-bed", output.getBedFilename(),
				output.getBimFilename(), output.getFamFilename(),
				"--input-map", mapFilename, "--output-max", output.getPrefix(),
				"--input-from", start + "", "--input-to", end + "");
		shapeIt.saveStdOut(output.getPrefix() + ".shapeit.out");
		shapeIt.saveStdErr(output.getPrefix() + ".shapeit.err");
		System.out.println("Command: " + shapeIt.getExecutedCommand());
		shapeIt.execute();

		// haps to vcf
		Command shapeItConvert = new Command(shapeItCommand);
		shapeItConvert.setSilent(false);
		shapeItConvert.setParams("-convert", "--input-haps",
				output.getPrefix(), "--output-vcf", output.getVcfFilename());
		System.out.println("Command: " + shapeItConvert.getExecutedCommand());

		return (shapeItConvert.execute() == 0);
	}

	public boolean imputeVCF(VcfChunk input, VcfChunkOutput output)
			throws InterruptedException, IOException {

		// mini-mac
		Command minimac = new Command(minimacCommand);
		minimac.setSilent(false);
		minimac.setParams("--refHaps", refPanelFilename, "--haps",
				output.getVcfFilename(), "--rounds", rounds + "", "--start",
				input.getStart() + "", "--end", input.getEnd() + "",
				"--window", minimacWindow + "", "--prefix", output.getPrefix(),
				"--chr", input.getChromosome(), "--noPhoneHome");

		minimac.saveStdOut(output.getPrefix() + ".minimac.out");
		minimac.saveStdErr(output.getPrefix() + ".minimac.err");

		System.out.println(minimac.getExecutedCommand());
		
		return (minimac.execute() == 0);

	}

	public int[] fixInfoFile(VcfChunk input, VcfChunkOutput output)
			throws IOException, InterruptedException {

		// fix window bug in minimac

		LineReader readerInfo = new LineReader(output.getInfoFilename());
		LineWriter writerInfo = new LineWriter(output.getInfoFixedFilename());

		readerInfo.next();
		String header = readerInfo.get();
		writerInfo.write(header);

		int startIndex = Integer.MAX_VALUE;
		int endIndex = 0;

		int index = 0;
		int snps = 0;
		while (readerInfo.next()) {
			String line = readerInfo.get();
			String[] tiles = line.split("\t", 2);
			int position = Integer.parseInt(tiles[0].split(":")[1]);
			if (position > input.getStart() && position < input.getEnd()) {
				startIndex = Math.min(startIndex, index);
				endIndex = Math.max(endIndex, index);
				writerInfo.write(line);
				snps++;
			}
			index++;
		}

		// log.info("After imputation: " + snps + " SNPs");

		readerInfo.close();
		writerInfo.close();

		return new int[] { startIndex, endIndex };

	}

	public String getHapiUrPreprocessCommand() {
		return hapiUrPreprocessCommand;
	}

	public void setHapiUrPreprocessCommand(String hapiUrPreprocessCommand) {
		this.hapiUrPreprocessCommand = hapiUrPreprocessCommand;
	}

	public String getMapFilename() {
		return mapFilename;
	}

	public void setMapFilename(String mapFilename) {
		this.mapFilename = mapFilename;
	}

}
