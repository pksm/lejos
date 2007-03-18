package lejos.nxt;

interface SimpleSensorPort extends SensorConstants {

	public int getMode();
	
	public int getType();
	
	public void setMode(int mode);
	
	public void setType(int type);
	
	public void setTypeAndMode(int type, int mode);

}
