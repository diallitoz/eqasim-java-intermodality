package ch.ethz.matsim.av.analysis.simulation;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.eqasim.core.analysis.AstraConvergence;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.groups.ControlerConfigGroup;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.events.IterationStartsEvent;
import org.matsim.core.controler.events.ShutdownEvent;
import org.matsim.core.controler.listener.IterationEndsListener;
import org.matsim.core.controler.listener.IterationStartsListener;
import org.matsim.core.controler.listener.ShutdownListener;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import ch.ethz.matsim.av.analysis.FleetDistanceListener;
import ch.ethz.matsim.av.analysis.LinkFinder;
import ch.ethz.matsim.av.analysis.passengers.PassengerAnalysisListener;
import ch.ethz.matsim.av.analysis.passengers.PassengerAnalysisWriter;
import ch.ethz.matsim.av.analysis.vehicles.VehicleAnalysisListener;
import ch.ethz.matsim.av.analysis.vehicles.VehicleAnalysisWriter;
import ch.ethz.matsim.av.config.AVConfigGroup;

@Singleton
public class AnalysisOutputListener implements IterationStartsListener, IterationEndsListener, ShutdownListener {
	private static final String PASSENGER_RIDES_FILE_NAME = "av_passenger_rides.csv";
	private static final String VEHICLE_MOVEMENTS_FILE_NAME = "av_vehicle_movements.csv";
	private static final String VEHICLE_ACTIVITIES_FILE_NAME = "av_vehicle_activities.csv";

	private final OutputDirectoryHierarchy outputDirectory;
	private int lastIteration = -1;

	private final int passengerAnalysisInterval;
	private final PassengerAnalysisListener passengerAnalysisListener;

	private final int vehicleAnalysisInterval;
	private final VehicleAnalysisListener vehicleAnalysisListener;

	private final boolean enableFleetDistanceListener;
	private final FleetDistanceListener fleetDistanceListener;

	private boolean isPassengerAnalysisActive = false;
	private boolean isVehicleAnalysisActive = false;

	private DistanceAnalysisWriter distanceAnalysisWriter;

	@Inject
	public AnalysisOutputListener(AVConfigGroup config, ControlerConfigGroup controllerConfig,
			OutputDirectoryHierarchy outputDirectory, Network network) {
		this.outputDirectory = outputDirectory;

		this.passengerAnalysisInterval = config.getPassengerAnalysisInterval();
		this.vehicleAnalysisInterval = config.getVehicleAnalysisInterval();
		this.enableFleetDistanceListener = config.getEnableDistanceAnalysis();

		LinkFinder linkFinder = new LinkFinder(network);

		this.passengerAnalysisListener = new PassengerAnalysisListener(linkFinder);
		this.vehicleAnalysisListener = new VehicleAnalysisListener(linkFinder);

		this.fleetDistanceListener = new FleetDistanceListener(config.getOperatorConfigs().keySet(), linkFinder);
		this.distanceAnalysisWriter = new DistanceAnalysisWriter(outputDirectory, config.getOperatorConfigs().keySet());
	}

	@Override
	public void notifyIterationStarts(IterationStartsEvent event) {
		if ((passengerAnalysisInterval > 0 && event.getIteration() % passengerAnalysisInterval == 0)
				|| AstraConvergence.IS_CONVERGED) {
			isPassengerAnalysisActive = true;
			event.getServices().getEvents().addHandler(passengerAnalysisListener);
		}

		if ((vehicleAnalysisInterval > 0 && event.getIteration() % vehicleAnalysisInterval == 0)
				|| AstraConvergence.IS_CONVERGED) {
			isVehicleAnalysisActive = true;
			event.getServices().getEvents().addHandler(vehicleAnalysisListener);
		}

		if (enableFleetDistanceListener) {
			event.getServices().getEvents().addHandler(fleetDistanceListener);
		}

		this.lastIteration = event.getIteration();
	}

	@Override
	public void notifyIterationEnds(IterationEndsEvent event) {
		try {
			if (isPassengerAnalysisActive) {
				event.getServices().getEvents().removeHandler(passengerAnalysisListener);

				String path = outputDirectory.getIterationFilename(event.getIteration(), PASSENGER_RIDES_FILE_NAME);
				new PassengerAnalysisWriter(passengerAnalysisListener).writeRides(new File(path));
			}

			if (isVehicleAnalysisActive) {
				event.getServices().getEvents().removeHandler(vehicleAnalysisListener);

				String movementsPath = outputDirectory.getIterationFilename(event.getIteration(),
						VEHICLE_MOVEMENTS_FILE_NAME);
				new VehicleAnalysisWriter(vehicleAnalysisListener).writeMovements(new File(movementsPath));

				String activitiesPath = outputDirectory.getIterationFilename(event.getIteration(),
						VEHICLE_ACTIVITIES_FILE_NAME);
				new VehicleAnalysisWriter(vehicleAnalysisListener).writeActivities(new File(activitiesPath));
			}

			if (enableFleetDistanceListener) {
				event.getServices().getEvents().removeHandler(fleetDistanceListener);
				distanceAnalysisWriter.write(fleetDistanceListener);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void notifyShutdown(ShutdownEvent event) {
		try {
			File iterationPath = new File(
					outputDirectory.getIterationFilename(lastIteration, PASSENGER_RIDES_FILE_NAME));
			File outputPath = new File(outputDirectory.getOutputFilename(PASSENGER_RIDES_FILE_NAME));
			Files.copy(iterationPath.toPath(), outputPath.toPath());
		} catch (IOException e) {
		}

		try {
			File iterationPath = new File(
					outputDirectory.getIterationFilename(lastIteration, VEHICLE_MOVEMENTS_FILE_NAME));
			File outputPath = new File(outputDirectory.getOutputFilename(VEHICLE_MOVEMENTS_FILE_NAME));
			Files.copy(iterationPath.toPath(), outputPath.toPath());
		} catch (IOException e) {
		}

		try {
			File iterationPath = new File(
					outputDirectory.getIterationFilename(lastIteration, VEHICLE_ACTIVITIES_FILE_NAME));
			File outputPath = new File(outputDirectory.getOutputFilename(VEHICLE_ACTIVITIES_FILE_NAME));
			Files.copy(iterationPath.toPath(), outputPath.toPath());
		} catch (IOException e) {
		}

		try {
			distanceAnalysisWriter.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
