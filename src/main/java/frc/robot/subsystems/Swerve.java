package frc.robot.subsystems;


import frc.robot.SwerveModule;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.ctre.phoenix6.configs.Pigeon2Configuration;
import com.ctre.phoenix6.hardware.Pigeon2;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.math.kinematics.SwerveDriveKinematics;
import edu.wpi.first.math.kinematics.SwerveDriveOdometry;
import edu.wpi.first.math.kinematics.SwerveModulePosition;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.Constants;

public class Swerve extends SubsystemBase {
    private static Swerve instance;

    public static Swerve getInstance() {
        if (instance == null) {
            instance = new Swerve();
        }
        return instance;
    }

    private SwerveDriveOdometry swerveOdometry;
    private Pigeon2 gyro;
    private SwerveModule[] mSwerveMods;
    public Supplier<Pose2d> poseSupplier = () -> getPose();
    public Consumer<Pose2d> poseConsumer = a -> {resetOdometry(a);};
    public BooleanSupplier inPosition = () -> inPosition();
    public Consumer<ChassisSpeeds> chassisConsumer = a -> {
        autoDrive(a, true);
    };
    public BooleanSupplier isPathRunningSupplier = () -> pathInProgress();
    public Pose2d goalPose = new Pose2d();
    public double xTolerance = 1.0;
    public double yTolerance = 1.0;
    public double rotTolerance = 30;

    // initializes the swerve modules
    public Swerve() {
        gyro = new Pigeon2(Constants.SwerveConstants.pigeonID, "2976 CANivore");
        gyro.getConfigurator().apply(new Pigeon2Configuration());

        mSwerveMods = new SwerveModule[] {
                new SwerveModule(0, Constants.SwerveConstants.Mod0.constants),
                new SwerveModule(1, Constants.SwerveConstants.Mod1.constants),
                new SwerveModule(2, Constants.SwerveConstants.Mod2.constants),
                new SwerveModule(3, Constants.SwerveConstants.Mod3.constants)
        };

        swerveOdometry = new SwerveDriveOdometry(Constants.SwerveConstants.swerveKinematics, getYaw(), getModulePositions());
    }

        // used to apply any driving movement to the swerve modules
    public void drive(Translation2d translation, double rotation, boolean fieldRelative, boolean isOpenLoop) {
        SwerveModuleState[] swerveModuleStates = Constants.SwerveConstants.swerveKinematics.toSwerveModuleStates(
                fieldRelative ? ChassisSpeeds.fromFieldRelativeSpeeds(
                        Math.copySign(Math.pow(translation.getX(), 2), translation.getX()), // square inputs for robert testing
                        Math.copySign(Math.pow(translation.getY(), 2), translation.getY()), // square inputs for robert testing
                        rotation,
                        getYaw())
                        : new ChassisSpeeds(
                                translation.getX(),
                                translation.getY(),
                                rotation));
        SwerveDriveKinematics.desaturateWheelSpeeds(swerveModuleStates, Constants.SwerveConstants.maxSpeed);

        for (SwerveModule mod : mSwerveMods) {
            mod.setDesiredState(swerveModuleStates[mod.moduleNumber], isOpenLoop);
        }
    }

        // never used but might be useful
    public void autoDrive(ChassisSpeeds chassisSpeeds, boolean isOpenLoop) {
        SwerveModuleState[] swerveModuleStates = Constants.SwerveConstants.swerveKinematics.toSwerveModuleStates(chassisSpeeds);
        SwerveDriveKinematics.desaturateWheelSpeeds(swerveModuleStates, Constants.SwerveConstants.maxSpeed);

        for (SwerveModule mod : mSwerveMods) {
            mod.setDesiredState(swerveModuleStates[mod.moduleNumber], isOpenLoop);
        }
    }

    // returns pose of the robot
    public Pose2d getPose() {
        return swerveOdometry.getPoseMeters();
    }

    // resets odometry to the provided pose
    public void resetOdometry(Pose2d pose) {
        swerveOdometry.resetPosition(pose.getRotation(), getModulePositions(), pose);
        gyro.setYaw(pose.getRotation().getDegrees());
    }

    // gets the state of the swerve modules
    public SwerveModuleState[] getModuleStates() {
        SwerveModuleState[] states = new SwerveModuleState[4];
        for (SwerveModule mod : mSwerveMods) {
            states[mod.moduleNumber] = mod.getState();
        }
        return states;
    }
    
    public void zeroGyro() {
        gyro.setYaw(0);
    }

    public Rotation2d getYaw() {
        return Rotation2d.fromDegrees(normalize(gyro.getYaw().getValue()));
    }

    public double getPitch() {
        return gyro.getRoll().getValueAsDouble();
    }

    // gyro reports angle cumalitvely not in 360s so this method gets the 360 
    // rotation and tranforms it to the 180 to -180 rotation that is used by the
    // pose class in WPIlib
    public static double normalize(double deg) {
        double angle = deg % 360;
        if (angle < -180) {
            angle = 180 - (Math.abs(angle) - 180);
        } else if (angle > 180) {
            angle = -180 + (Math.abs(angle) - 180);
        }
        return angle;
    }

    // gets the positions of the swerve modules
    public SwerveModulePosition[] getModulePositions() {
        SwerveModulePosition[] positions = new SwerveModulePosition[4];
        for (SwerveModule mod : mSwerveMods) {
            positions[mod.moduleNumber] = mod.getPosition();
        }
        return positions;
    }

    // checks if the swerve is being used for an auto generated path like OTF or auto
    public boolean pathInProgress() {
        return !getDefaultCommand().isScheduled();
    }

    // these two methods below are used in auto to set the parameters for the goal pose range and execute commands in
    // auto when the robot gets within that range of the goal pose
    public void goalPoseParameters(Pose2d goalPose, double xTolerance, double yTolerance, double rotTolerance) {
        this.goalPose = goalPose;
        this.xTolerance = xTolerance;
        this.yTolerance = yTolerance;
    }

    public boolean inPosition() {
        return (Math.abs(getPose().getX() - goalPose.getX()) < xTolerance)
                && (Math.abs(getPose().getY() - goalPose.getY()) < yTolerance)
                && (Math.abs(getPose().getRotation().getDegrees()
                        - goalPose.getRotation().getDegrees()) < rotTolerance);
    }

    @Override
    public void periodic() {

    }
}
