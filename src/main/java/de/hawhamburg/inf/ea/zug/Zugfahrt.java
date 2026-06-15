package de.hawhamburg.inf.ea.zug;

import io.jenetics.Genotype;
import io.jenetics.Mutator;
import io.jenetics.engine.Engine;
import io.jenetics.engine.EvolutionResult;
import io.jenetics.engine.Limits;
import io.jenetics.ext.SingleNodeCrossover;
import io.jenetics.ext.util.TreeNode;
import io.jenetics.prog.ProgramChromosome;
import io.jenetics.prog.ProgramGene;
import io.jenetics.prog.op.Const;
import io.jenetics.prog.op.MathOp;
import io.jenetics.prog.op.Op;
import io.jenetics.util.ISeq;

/**
 * @author chris
 */
public class Zugfahrt {

    public static double error(Genotype<ProgramGene<Double>> ind) {
        Zug zug = new Zug();

        // Setze die Zug instance bei allen Operationen und Terminalen
        for (var op : ind.gene().operations()) {
            if (op instanceof Zug.Operation operation) {
                operation.setZug(zug);
            }
        }

        for (var op : ind.gene().terminals()) {
            if (op instanceof Zug.Operation operation) {
                operation.setZug(zug);
            }
        }

        var gene = ind.gene(); // hol den GP-Baum aus diesem Individuum heraus

        int schritte = 0; // Schritte = Zeit
        for (int i = 0; i < 100; i++) { // Maximal 100 Schritte
            // Wir rufen das Kontrollprogramm des Zugs auf.
            // Je nach Entfernung (und ggfls. aktueller Geschwindigkeit) soll
            // das Programm die neue Geschwindigkeit des Zuges setzen.
            // Der Kontroll-Loop wird durch die For-Schleife hier repräsentiert,
            // weil dies in GP schwierig als Operation darzustellen ist.
            gene.eval(); // frage den Regler EINMAL: setzt neue Geschwindigkeit und werte diesen Baum rekursiv aus

            // Wir lassen Entfernung und Energie aktualisieren
            zug.tick(); // ein Zeitschritt: Zug rollt, Energie steigt
            schritte++;
            if (zug.getEntfernung() - 1000.0 >= 0) {
                break;
            }
        }

        double distanz = Math.abs(zug.getEntfernung() - 1000);
        double energie = zug.getEnergie();
        double zeit = schritte;

        // Gewichtungen
        double w1 = 100.0;   // Entfernung: höchste Priorität (kleine Zahl, also stark gewichten)
        double w2 = 0.01;    // Energie: große Zahl, also klein gewichten, damit sie nicht alles dominiert
        double w3 = 1.0;     // Zeit: mittlere Bedeutung

        return w1 * distanz + w2 * energie + w3 * zeit;
    }

    public static void main(String[] args) {
        final ISeq<Op<Double>> operations = ISeq.of( // unveränderliche Liste
                new Zug.SetSpeed(),
                new Zug.IfElse(),
                MathOp.ADD, MathOp.SUB);

        final ISeq<Op<Double>> terminals = ISeq.of(
                new Zug.GetSpeed(),
                new Zug.GetDistance(),
                Const.of(0.0), Const.of(1.0), Const.of(2.0), Const.of(3.0),
                Const.of(-1.0), Const.of(-2.0), Const.of(-3.0)
        );

        final ProgramChromosome<Double> program // Bauplan für Bäume
                = ProgramChromosome.of(10, operations, terminals); // Baumtiefe: 10

        final Engine<ProgramGene<Double>, Double> engine = Engine // ProgramGene = EIN GP-Baum
                .builder(Zugfahrt::error, program) // Fitnessfunktion + Baum-Bauplan
                .minimizing() // kleiner Fitnesswert = besser
                .alterers( // welche genetischen Operatoren?
                        new SingleNodeCrossover<>(), // Subtree-Crossover
                        new Mutator<>()) // Subtree-Mutation
                .build();

        final EvolutionResult<ProgramGene<Double>, Double> result = engine // Evolution laufen lassen
                .stream()
                .limit(Limits.byFixedGeneration(10))
                .collect(EvolutionResult.toBestEvolutionResult());


        final TreeNode<Op<Double>> tree = result.bestPhenotype()
                .genotype().gene().toTreeNode();

        System.out.println("Generations: " + result.totalGenerations());
        System.out.println("Function:    " + tree);
        System.out.println("Error:       " + error(result.bestPhenotype().genotype()));
    }
}
