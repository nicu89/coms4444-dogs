package dogs.g3;

import dogs.g3.PlayerGraph;
import dogs.g3.PlayerGraph.GraphType;

import java.util.*;

import javax.swing.plaf.synth.SynthSeparatorUI;

import dogs.sim.Directive;
import dogs.sim.Directive.Instruction;
import dogs.sim.Dictionary;
import dogs.sim.Dog;
import dogs.sim.Owner;
import dogs.sim.Owner.OwnerName;
import dogs.sim.ParkLocation;
import dogs.sim.SimPrinter;
import java.io.StringWriter;

public class Player extends dogs.sim.Player {
	private List<Owner> otherOwners;
	private String identification_signal = "three";
	private List<Dog> myDogs;
	private Integer round;
	private Owner myOwner;
	private double listeningProbability = 0.2;
	HashMap<String, ParkLocation> positions;
	private List<Dog> allDogs;
	HashMap<String, String> nextOwners;
	PlayerGraph graph;
	private int count = 0;
	private int numCenterPlayers = 0;
	/**
	 * Player constructor
	 *
	 * @param rounds          number of rounds
	 * @param numDogsPerOwner number of dogs per owner
	 * @param numOwners       number of owners
	 * @param seed            random seed
	 * @param simPrinter      simulation printer
	 *
	 */
	public Player(Integer rounds, Integer numDogsPerOwner, Integer numOwners, Integer seed, Random random,
			SimPrinter simPrinter) {
		super(rounds, numDogsPerOwner, numOwners, seed, random, simPrinter);
	}

	private HashMap<String, ParkLocation> mapOwnerToParkLocationCircle(List<Owner> owners, ParkLocation center,
			int radius) {
		HashMap<String, ParkLocation> ownerToLocation = new HashMap<String, ParkLocation>();
		if (this.graph.graphType == GraphType.SPOKE) {
			//System.out.println("HERE");
			numCenterPlayers = (int) Math.ceil((double) radius / 51.0);
			//System.out.println(radius + " " + numCenterPlayers);
			int distanceBetweenSpokes = radius * 2 / (numCenterPlayers + 1);
			int northPlayerY = 2000;
			Owner northPlayer = null;
			int westPlayerX = 2000;
			Owner westPlayer = null;
			for (int i = numCenterPlayers; i < owners.size(); i++) {
				Owner current = owners.get(i);
				double x = center.getRow() + radius * Math.cos((2 * Math.PI * i) / (owners.size() - numCenterPlayers));
				if (westPlayerX > x) {
					westPlayer = current;
					westPlayerX = (int) x;
				}
				double y = center.getColumn() + radius * Math.sin((2 * Math.PI * i) / (owners.size() - numCenterPlayers));
				if (northPlayerY > y) {
					northPlayer = current;
					northPlayerY = (int) y;
				}
				ownerToLocation.put(current.getNameAsString(), new ParkLocation(x, y));
			}
			double centerX = westPlayerX + radius;
			int prevY = northPlayerY;
			for (int i = 0; i < numCenterPlayers; i++) {
				Owner current = owners.get(i);
				ownerToLocation.put(current.getNameAsString(),
						new ParkLocation(centerX, (double) prevY + distanceBetweenSpokes));
			}
			System.out.println(westPlayer.getNameAsString());
		} else {
			for (int i = 0; i < owners.size(); i++) {
				Owner current = owners.get(i);
				double x = center.getRow() + radius * Math.cos((2 * Math.PI * i) / owners.size());
				double y = center.getColumn() + radius * Math.sin((2 * Math.PI * i) / owners.size());
				ownerToLocation.put(current.getNameAsString(), new ParkLocation(x, y));
			}
		}
		return ownerToLocation;
	}

	/**
	* Build a Graph with owners as nodes
	* @param owners list of owners
	*/
	private PlayerGraph buildPlayerGraph(List<Owner> owners) {
		PlayerGraph graph = new PlayerGraph(owners);
		//add an edge between each pair of owners within 40 meters of each other
		for (Owner o1 : owners){
			for (Owner o2 : owners){
				if ((o1.getNameAsEnum() != o2.getNameAsEnum()) && (distanceBetweenOwners(o1, o2) <= 40.0)) {
					graph.addConnection(o1, o2);
				}
			}
		}
		graph.printGraph(simPrinter);
		return graph;
	}

	private ParkLocation getNextLocation(ParkLocation currentLocation, ParkLocation desiredLocation) {
		double x_difference = desiredLocation.getRow() - currentLocation.getRow();
		double y_difference = desiredLocation.getColumn() - currentLocation.getColumn();
		double distance = Math.sqrt(Math.pow(x_difference, 2) + Math.pow(y_difference, 2));
		if (distance < 5) {
			return desiredLocation;
		}
		double x_difference_normalized = x_difference / distance;
		double y_difference_normalized = y_difference / distance;
		return new ParkLocation(currentLocation.getRow() + x_difference_normalized * 2,
				currentLocation.getColumn() + y_difference_normalized * 2);
	}

	/**
	 * Choose command/directive for next round
	 *
	 * @param round       current round
	 * @param myOwner     my owner
	 * @param otherOwners all other owners in the park
	 * @return a directive for the owner's next move
	 *
	 */
	public Directive chooseDirective(Integer round, Owner myOwner, List<Owner> otherOwners) {
		this.round = round;
		this.myDogs = myOwner.getDogs();
		this.allDogs = getAllDogs(myOwner, otherOwners);
		this.otherOwners = otherOwners;
		this.myOwner = myOwner;

		try {
			List<Owner> allOwners = otherOwners;
			allOwners.add(myOwner);
			sortOwners();
			Directive directive = new Directive();
			// if(round == 6){
			// 	System.out.println(getOtherOwnersSignals(otherOwners).toString());
			// }
			if (round == 1) {
				List<Owner> alphabeticalOwners = sortOwnersAlphabetically(allOwners);
				double radius = 40 * (allOwners.size()-1);
				radius /= (double) (2.0 * Math.PI);
				//System.out.println(radius + " " + allOwners.size());
				radius = 20;
				this.graph = buildPlayerGraph(alphabeticalOwners);
				this.graph.graphType = this.graph.getGraphType(allOwners);
				// if(this.graph.graphType == GraphType.SPOKE){
				// 	numCenterPlayers = (int) Math.ceil((double) radius / 51.0);
				// 	radius = 30 * (this.otherOwners.size()-numCenterPlayers);
				// 	radius /= (double) (2.0 * Math.PI);
				// }
				HashMap<String, ParkLocation> currentPositions = mapOwnerToParkLocationCircle(alphabeticalOwners,
						new ParkLocation(25.0, 25.0), (int)radius);
				this.positions = currentPositions;
				simPrinter.println("calling signal...");
				directive.instruction = Instruction.CALL_SIGNAL;
				directive.signalWord = this.identification_signal;
				return directive;
			}

			// System.out.println(this.graph.graphType);
			ParkLocation currentLocation = myOwner.getLocation();
			ParkLocation desiredLocation = this.positions.get(myOwner.getNameAsString());

			if (Math.abs(currentLocation.getRow() - desiredLocation.getRow()) > 1.0
					|| Math.abs(currentLocation.getColumn() - desiredLocation.getColumn()) > 1.0) {
				directive.instruction = Instruction.MOVE;
				ParkLocation next = getNextLocation(currentLocation, desiredLocation);
				directive.parkLocation = next;
				return directive;
			}

			/*
			 * if(round <= 151) { directive.instruction = Instruction.MOVE;
			 * directive.parkLocation = new ParkLocation(myOwner.getLocation().getRow() + 2,
			 * myOwner.getLocation().getColumn() + 2); return directive; } else if (round <=
			 * 271) { directive.instruction = Instruction.MOVE; directive.parkLocation = new
			 * ParkLocation(myOwner.getLocation().getRow(),
			 * myOwner.getLocation().getColumn() + 2); return directive; }
			 */

			// get waiting dogs and separate into others' dogs and my own dogs
			List<Dog> waitingDogs = getWaitingDogs(myOwner, otherOwners);
			List<Dog> notOwnedByMe = new ArrayList<Dog>();
			List<Dog> ownedByMe = new ArrayList<Dog>();
			for (Dog dog : waitingDogs) {
				if (!dog.getOwner().equals(this.myOwner)) {
					notOwnedByMe.add(dog);
					//simPrinter.println("found");
				} else {
					ownedByMe.add(dog);
				}
			}
			// Owner alice = null;
			// for(int i = 0; i < this.otherOwners.size(); i++){
			// 	if(this.otherOwners.get(i).getNameAsEnum() == OwnerName.ALICE){
			// 		alice = this.otherOwners.get(i);
			// 	}
			// }
			//System.out.println(this.graph.getConnections(alice).toString());
			List<Dog> sortedDogs = new ArrayList<>();
			for (Dog d : ownedByMe) {
				sortedDogs.add(d);
			}
			List<Dog> sortedDogsNotMine = sortDogsByRemainingWaitTime(notOwnedByMe);

			for (Dog d : sortedDogsNotMine) {
				sortedDogs.add(d);
			}
			// out.println(waitingDogs.size() + " " + sortedDogs.size() + " " +
			// sortedDogsNotMine.size() + " " + notOwnedByMe.size() + " " +
			// ownedByMe.size());
			if (!sortedDogs.isEmpty()) {
				directive.instruction = Instruction.THROW_BALL;
				directive.dogToPlayWith = sortedDogs.get(0);
				List<OwnerName> neighborNames = this.graph.getConnections(myOwner);
				
				//throw the ball toward the least occupied neighbor
				List<Owner> neighbors = new ArrayList<>();
				for (Owner owner : this.otherOwners) {
					if (neighborNames.contains(owner.getNameAsEnum())) {
						neighbors.add(owner);
					}
				}
				Owner requiredOwner = getLeastBusyNeighbor(neighbors, this.allDogs);
				OwnerName throwToOwnerName = requiredOwner.getNameAsEnum();

				/*Owner requiredOwner = null; 
				for (Owner throwOwner: this.otherOwners) {
					if (throwOwner.getNameAsEnum() == throwToOwnerName) {
						requiredOwner = throwOwner;
						break;
					}
				}*/
				
				Double ballRow = requiredOwner.getLocation().getRow();
				Double ballColumn = requiredOwner.getLocation().getColumn();
				Random r = new Random();
				count += 1;
				//simPrinter.println(myOwner.getNameAsString() + " " + sortedDogs.size());
				//simPrinter.println(throwToOwnerName + " from " + myOwner.getNameAsString());
				directive.parkLocation = new ParkLocation(ballRow, ballColumn);
				return directive;
			}

			/*
			 * 
			 * //if any of my own dogs are waiting, throw the ball for the least exercised
			 * dog to some other owner if (!ownedByMe.isEmpty()){ directive.instruction =
			 * Instruction.THROW_BALL; List<Dog> sortedMyDogs = sortDogs(ownedByMe);
			 * directive.dogToPlayWith = sortedMyDogs.get(0); for (Owner throwOwner :
			 * this.otherOwners){ Double dist = distanceBetweenOwners(throwOwner,
			 * this.myOwner); //System.out.println(dist); if (dist < 40) { Double ballRow =
			 * throwOwner.getLocation().getRow(); Double ballColumn =
			 * throwOwner.getLocation().getColumn(); Random r = new Random(); ballRow +=
			 * r.nextInt(5 + 5) - 5; directive.parkLocation = new ParkLocation(ballRow,
			 * ballColumn); return directive; } }
			 * 
			 * //if all other owners are >40 distance away, throw the ball randomly double
			 * randomDistance = 40.0; double randomAngle =
			 * Math.toRadians(random.nextDouble() * 360); double ballRow =
			 * myOwner.getLocation().getRow() + randomDistance * Math.sin(randomAngle);
			 * double ballColumn = myOwner.getLocation().getColumn() + randomDistance *
			 * Math.cos(randomAngle); if(ballRow < 0.0) ballRow = 0.0; if(ballRow >
			 * ParkLocation.PARK_SIZE - 1) ballRow = ParkLocation.PARK_SIZE - 1;
			 * if(ballColumn < 0.0) ballColumn = 0.0; if(ballColumn > ParkLocation.PARK_SIZE
			 * - 1) ballColumn = ParkLocation.PARK_SIZE - 1; directive.parkLocation = new
			 * ParkLocation(ballRow, ballColumn); return directive;
			 * 
			 * }
			 * 
			 * //if any of the others' dogs is waiting, throw the ball back to its owner if
			 * (!ownedByMe.isEmpty()) { directive.instruction = Instruction.THROW_BALL;
			 * List<Dog> sortedOtherDogs = sortDogs(notOwnedByMe); directive.dogToPlayWith =
			 * sortedOtherDogs.get(0); Owner throwOwner =
			 * directive.dogToPlayWith.getOwner(); Double dist =
			 * distanceBetweenOwners(throwOwner, this.myOwner); if (dist < 40) { Double
			 * ballRow = throwOwner.getLocation().getRow(); Double ballColumn =
			 * throwOwner.getLocation().getColumn(); Random r = new Random(); ballRow +=
			 * r.nextInt(5 + 5) - 5; directive.parkLocation = new ParkLocation(ballRow,
			 * ballColumn); return directive; }
			 * 
			 * //if its owner is >40 distance away, throw the ball in its owner's direction
			 * for 40 meters double dx = throwOwner.getLocation().getRow() -
			 * myOwner.getLocation().getRow(); double dy =
			 * throwOwner.getLocation().getColumn() - myOwner.getLocation().getColumn();
			 * double ballRow = myOwner.getLocation().getRow() + dx * (40.0/dist); double
			 * ballColumn = myOwner.getLocation().getColumn() + dy * (40.0/dist); if(ballRow
			 * < 0.0) ballRow = 0.0; if(ballRow > ParkLocation.PARK_SIZE - 1) ballRow =
			 * ParkLocation.PARK_SIZE - 1; if(ballColumn < 0.0) ballColumn = 0.0;
			 * if(ballColumn > ParkLocation.PARK_SIZE - 1) ballColumn =
			 * ParkLocation.PARK_SIZE - 1; directive.parkLocation = new
			 * ParkLocation(ballRow, ballColumn); return directive; }
			 */

			// otherwise do nothing
			directive.instruction = Instruction.NOTHING;
			return directive;
		} catch (Exception e) {
			//System.out.println(e.toString());
			e.printStackTrace();
		}
		Directive directive = new Directive();
		return directive;
	}

	private List<String> getOtherOwnersSignals(List<Owner> otherOwners) {
		List<String> otherOwnersSignals = new ArrayList<>();
		for (Owner otherOwner : otherOwners)
			if (!otherOwner.getCurrentSignal().equals("_"))
				otherOwnersSignals.add(otherOwner.getCurrentSignal());
		return otherOwnersSignals;
	}

	private List<Dog> getWaitingDogs(Owner myOwner, List<Owner> otherOwners) {
		List<Dog> waitingDogs = new ArrayList<>();
		int count = 0;
		for (Dog dog : myOwner.getDogs()) {
			if (dog.isWaitingForItsOwner()) {
				waitingDogs.add(dog);
				count += 1;
			}
		}
		for (Owner otherOwner : otherOwners) {
			for (Dog dog : otherOwner.getDogs()) {
				if (dog.isWaitingForOwner(myOwner)) {
					waitingDogs.add(dog);
					//simPrinter.println("Found Other Dog Thats Not Ours");
					count += 1;
				}
			}
		}
		// System.out.println(myOwner.getNameAsString() + " " + count);
		return waitingDogs;
	}

	/* SORTING */
	private List<Dog> sortDogs(List<Dog> dogList) {
		Collections.sort(dogList, new Comparator<Dog>() {
			@Override
			public int compare(Dog u1, Dog u2) {
				return compareDogs(u1, u2);
			}
		});
		return dogList;
	}

	private List<Dog> sortDogsByRemainingWaitTime(List<Dog> dogList) {
		Collections.sort(dogList, new Comparator<Dog>() {
			@Override
			public int compare(Dog u1, Dog u2) {
				return u1.getWaitingTimeRemaining().compareTo(u2.getWaitingTimeRemaining());
			}
		});
		// for(Dog d: dogList){
		// System.out.println(d.getWaitingTimeRemaining());
		// }
		return dogList;
	}

	private int compareDogs(Dog u1, Dog u2) {
		return u2.getExerciseTimeRemaining().compareTo(u1.getExerciseTimeRemaining());
	}

	private void sortOwners() {
		Collections.sort(this.otherOwners, new Comparator<Owner>() {
			@Override
			public int compare(Owner u1, Owner u2) {
				return compareOwners(u1, u2);
			}
		});
	}

	private List<Owner> sortOwnersAlphabetically(List<Owner> owners) {
		Collections.sort(owners, new Comparator<Owner>() {
			@Override
			public int compare(Owner u1, Owner u2) {
				return u1.getNameAsString().compareTo(u2.getNameAsString());
			}
		});
		return owners;
	}

	private int compareOwners(Owner u1, Owner u2) {
		Double distanceToOwner1 = Math.pow(u1.getLocation().getRow() - this.myOwner.getLocation().getRow(), 2);
		distanceToOwner1 += Math.pow(u1.getLocation().getColumn() - this.myOwner.getLocation().getColumn(), 2);

		Double distanceToOwner2 = Math.pow(u2.getLocation().getRow() - this.myOwner.getLocation().getRow(), 2);
		distanceToOwner2 += Math.pow(u2.getLocation().getColumn() - this.myOwner.getLocation().getColumn(), 2);

		return distanceToOwner2.compareTo(distanceToOwner1);
	}

	private Double distanceBetweenOwners(Owner u1, Owner u2) {
		Double dX = u1.getLocation().getRow() - u2.getLocation().getRow();
		Double dY = u1.getLocation().getColumn() - u2.getLocation().getColumn();
		Double dist = Math.sqrt(dX * dX + dY * dY);
		return dist;
	}

	/**
	* Get the least occupied owner based on 
	* the # of dogs waiting for them + # of dogs heading toward them
	* @param neighbors    a list of neighbor owners
	* @param allDogs      a list of all dogs in the park
	**/
	private Owner getLeastBusyNeighbor(List<Owner> neighbors, List<Dog> allDogs) {
		HashMap<Owner, Integer> busyMap = new HashMap<Owner, Integer>();
		for (Owner owner : neighbors) {
			busyMap.put(owner, 0);
		}

		//loop through all dogs to check if they are heading to/waiting for someone
		for (Dog dog : allDogs) {
			if (dog.isWaitingForPerson()) {
				Owner ownerWaited = dog.getOwnerWaitingFor();
				if (neighbors.contains(ownerWaited)){
					busyMap.put(ownerWaited, busyMap.get(ownerWaited) + 1);
				}
			}

			if (dog.isHeadingForPerson()) {
				Owner ownerHeadingFor = dog.getOwnerHeadingFor();
				if (neighbors.contains(ownerHeadingFor)){
					busyMap.put(ownerHeadingFor, busyMap.get(ownerHeadingFor) + 1);
				}
			}
		}

		//find the neighbor with the least number of dogs waiting for/heading to 
		Owner leastBusyOwner = neighbors.get(0);
		for (Owner neighbor : neighbors) {
			if (busyMap.get(neighbor) < busyMap.get(leastBusyOwner)){
				leastBusyOwner = neighbor;
			}
		}

		return leastBusyOwner;
	}

	/**
	* return a list of all dogs in the configuration
	**/
	private List<Dog> getAllDogs(Owner myOwner, List<Owner> otherOwners) {
		List<Dog> allDogs = new ArrayList<>();
		allDogs.addAll(myOwner.getDogs());
		for (Owner owner : otherOwners){
			allDogs.addAll(owner.getDogs());
		}
		return allDogs;
	}
}