package warcom.pdfscore;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.Stack;
import java.util.UUID;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class AppActivity extends Activity implements OnClickListener {

    //constantes
    //==========================================================

    //View seleccionado de nuestro layout
    interface CurrentView {
        int READ_LAYOUT = 1;
        int PDF_SELECTION_LAYOUT = 2;
        int CONNECTBT_LAYOUT = 3;
    }

    final int RECEIVE_MSG = 1;

    //comandos Bluetooth
    final String NEXT_CMD = "N";
    final String PREV_CMD = "P";
    final String CHECK_CMD = "C";
    //respuestas BT
    final String STATE_ST = "OK";
    final String DISCONNECT_ST = "BYE";

    //objetos IU
    //==========================================================

    //layouts
    LinearLayout readLayout;
    LinearLayout pdfSelectionLayout;
    LinearLayout connectBTLayout;

    //objetos layout conectar
    ListView devicelist;
    //objetos layout seleccion pdf
    ListView pdfList;
    //objetos layout lectura pdf
    com.joanzapata.pdfview.PDFView pdfViewer;
    Button next;
    Button previous;
    TextView txtStatus;

    //variables globales
    //==========================================================
    private static int currentView;
    // Background task to generate pdf file listing
    PdfListLoadTask listTask;
    // Adapter to list view
    ArrayAdapter<String> adapter;
    // array of pdf files
    File[] filelist;
    // index to track currentPage in rendered Pdf
    private static int currentPage = 1;
    String openedPdfFileName;
    // File Descriptor for rendered Pdf file
    private ParcelFileDescriptor mFileDescriptor;

    private BluetoothAdapter myBluetooth = null;
    //private Set pairedDevices;
    private  String address;
    private ProgressDialog progress;
    BluetoothSocket btSocket = null;
    private boolean isBtConnected = false;
    static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    static Handler h;
    private StringBuilder sb = new StringBuilder();
    private ConnectedThread mConnectedThread;

    private boolean openedPDF = false;
    private ArrayList<String> filePathList;
    private ArrayList<Integer> fileTypeList;
    private String currentPath;
    private Stack<String> filePathStack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app);

        //asociamos objetos de layout
        readLayout = (LinearLayout) findViewById(R.id.read_layout);
        pdfSelectionLayout = (LinearLayout) findViewById(R.id.pdf_selection_layout);
        connectBTLayout = (LinearLayout) findViewById(R.id.connectBT_layout);

        //definimos los listeners de objetos clickeables
        devicelist = (ListView)findViewById(R.id.lstDevices);
        next = (Button) findViewById(R.id.next);
        previous = (Button) findViewById(R.id.previous);
        pdfList = (ListView) findViewById(R.id.pdfList);
        txtStatus = (TextView) findViewById(R.id.txtStatus);
        pdfViewer = (com.joanzapata.pdfview.PDFView) findViewById(R.id.pdfViewer);

        //definimos listeners
        findViewById(R.id.btFind).setOnClickListener(this);

        next.setOnClickListener(this);
        previous.setOnClickListener(this);
        pdfList.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                //si clickeamos en directorio, lo abrimos, si es archivo, cargamos el renderer
                if (fileTypeList.get(position) == 0){//directorio
                    if (listTask != null)
                        listTask.cancel(true);
                    listTask = new PdfListLoadTask();
                    listTask.execute(filePathList.get(position));
                }
                else{
                    openedPdfFileName = adapter.getItem(position);

                    pdfViewer.fromFile(filelist[position])
                            .onLoad(pdfLoadCompleteListener)
                            .onPageChange(pdfPageChangedListener)
                            .load();

                    openedPDF = true;
                    currentPage = 1;
                }
            }
        });

        //definimos handler que gestionara los mensajes BT
        h = new Handler() {
            public void handleMessage(android.os.Message msg) {
                switch (msg.what) {
                    case RECEIVE_MSG:                                                   // if receive massage
                        txtStatus.setText("Cmd:");
                        //byte[] readBuf = (byte[]) msg.obj;
                        //String strIncom = new String(readBuf, 0, msg.arg1);                 // create string from bytes array
                        String strIncom = (String) msg.obj;
                        sb.append(strIncom);                                                // append string
                        int endOfLineIndex = sb.indexOf("\r\n");                            // determine the end-of-line
                        if (endOfLineIndex > 0) {                                            // if end-of-line,
                            String sbprint = sb.substring(0, endOfLineIndex);               // extract string
                            sb.delete(0, sb.length());                                      // and clear
                            //debug receive data
                            //Toast.makeText(getApplicationContext(), sbprint.toString(), Toast.LENGTH_SHORT).show();
                            txtStatus.setText("Cmd:"+sbprint.toString()+" ("+System.currentTimeMillis()+")");

                            //process data
                            if (sbprint.toString().equals(NEXT_CMD)){
                                if(currentPage < pdfViewer.getPageCount())
                                {
                                    currentPage++;
                                    pdfViewer.jumpTo(currentPage);
                                }
                            }
                            if (sbprint.toString().equals(PREV_CMD)){
                                if(currentPage > 1)
                                {
                                    currentPage--;
                                    pdfViewer.jumpTo(currentPage);
                                }
                            }
                            if (sbprint.toString().equals(CHECK_CMD)){
                                //log para comprobar duracion baterias
                                Log.d("PDFSCORE","Pedal pide check");
                                mConnectedThread.write(STATE_ST);
                            }
                        }

                        break;
                }
            };
        };

        //obtenemos adaptador bluetooth
        myBluetooth = BluetoothAdapter.getDefaultAdapter();
        if(myBluetooth == null)
        {
            //Mensaje no hay adaptador blueTooth
            Toast.makeText(getApplicationContext(), "No existe adaptador Bluetooth", Toast.LENGTH_LONG).show();
            //finish apk
            exitApp();
        }
        else
        {
            if (myBluetooth.isEnabled())
            { }
            else
            {
                //Pedimos activar el BT
                Intent turnBTon = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(turnBTon,1);
            }
        }

        //ruta por defecto al abrir el explorador
        currentPath = Environment.getExternalStorageDirectory().getPath();
        filePathStack = new Stack();

        //visualizamos layout principal
        updateView(CurrentView.PDF_SELECTION_LAYOUT);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_connect) {
            updateView(CurrentView.CONNECTBT_LAYOUT);
            return true;
        }
        if (id == R.id.action_disconnect) {
            if (isBtConnected)
                try {
                    mConnectedThread.write(DISCONNECT_ST);
                    btSocket.close();
                    isBtConnected = false;
                    mConnectedThread.running = false;
                    Toast.makeText(getApplicationContext(), "Bluetooth Desconectado", Toast.LENGTH_SHORT).show();
                }
                catch (IOException ex)
                {
                    Log.d("DEBUG",ex.toString());
                }
            else
            {
                Toast.makeText(getApplicationContext(), "No hay conexión realizada", Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        if (id == R.id.action_openPDF) {
            if (!filePathStack.isEmpty())
                filePathStack.pop();
            if (listTask != null)
                listTask.cancel(true);
            listTask = new PdfListLoadTask();
            listTask.execute(currentPath.toString());
            currentPage = 1;
            updateView(CurrentView.PDF_SELECTION_LAYOUT);
            return true;
        }
        if (id == R.id.action_close) {
            exitApp();
            return true;
        }
        if (id == R.id.action_about) {
            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
            alertDialogBuilder.setTitle("PDFScore");
            alertDialogBuilder.setMessage("Version: " + BuildConfig.VERSION_NAME + "\nWarrior - Warcom Soft.");
            alertDialogBuilder.create().show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    //acciones de la tecla Atras
    @Override
    public void onBackPressed() {
        if (currentView == CurrentView.READ_LAYOUT) {
            //saltamos a elegir pdf
            updateView(CurrentView.PDF_SELECTION_LAYOUT);
        }
        if (currentView == CurrentView.PDF_SELECTION_LAYOUT) {
            //abrimos la ruta anterior
            if (listTask != null)
                listTask.cancel(true);
            listTask = new PdfListLoadTask();
            filePathStack.pop();
            if (!filePathStack.isEmpty())
                listTask.execute(filePathStack.pop());
            else
                listTask.execute(Environment.getExternalStorageDirectory().getPath());
        } else if (currentView == CurrentView.CONNECTBT_LAYOUT) {
           //si teniamos pdf abierto, volvemos a él, si no, a seleccion
           if (openedPDF){
               updateView(CurrentView.READ_LAYOUT);
               updateActionBarText();
           }
            else
               updateView(CurrentView.PDF_SELECTION_LAYOUT);
        } else {
            super.onBackPressed();
        }
    }

    //actualizar el titulo del action bar
    private void updateActionBarText() {

        if (currentView == CurrentView.READ_LAYOUT) {
            int index = pdfViewer.getCurrentPage()+1;
            int pageCount = pdfViewer.getPageCount();

            previous.setEnabled(1 != index);
            next.setEnabled(index  < pageCount);
            getActionBar().setTitle(
                    openedPdfFileName + "(" + (index ) + "/" + pageCount
                            + ")");

        }else if (currentView == CurrentView.PDF_SELECTION_LAYOUT) {
            getActionBar().setTitle(currentPath.replace("/storage/emulated/0","PDFScore"));
        }else {
            getActionBar().setTitle(R.string.app_name);
        }
    }

    //actualizar el view del layout
    private void updateView(int updateView) {
        switch (updateView) {
            case CurrentView.READ_LAYOUT:
                currentView = CurrentView.READ_LAYOUT;

                readLayout.setVisibility(View.VISIBLE);
                pdfSelectionLayout.setVisibility(View.INVISIBLE);
                connectBTLayout.setVisibility(View.INVISIBLE);
                updateActionBarText();
                break;
            case CurrentView.PDF_SELECTION_LAYOUT:
                currentView = CurrentView.PDF_SELECTION_LAYOUT;

                if (listTask != null)
                    listTask.cancel(true);
                listTask = new PdfListLoadTask();
                listTask.execute(currentPath);
                openedPDF = false;

                readLayout.setVisibility(View.INVISIBLE);
                pdfSelectionLayout.setVisibility(View.VISIBLE);
                connectBTLayout.setVisibility(View.INVISIBLE);
                updateActionBarText();
                break;
            case CurrentView.CONNECTBT_LAYOUT:
                currentView = CurrentView.CONNECTBT_LAYOUT;

                readLayout.setVisibility(View.INVISIBLE);
                pdfSelectionLayout.setVisibility(View.INVISIBLE);
                connectBTLayout.setVisibility(View.VISIBLE);
                updateActionBarText();
                break;
        }
    }

    //handlers de los eventos click
    @Override
    public void onClick(View v) {
        int viewId = v.getId();
        switch (viewId) {
            case R.id.next:
                currentPage++;
                pdfViewer.jumpTo(currentPage);

                break;
            case R.id.previous:
                currentPage--;
                pdfViewer.jumpTo(currentPage);
                break;
            case R.id.btFind:
                pairedDevicesList();
                break;
        }

    }

    //handler de loadCOmplete del PDF
    private com.joanzapata.pdfview.listener.OnLoadCompleteListener pdfLoadCompleteListener = new com.joanzapata.pdfview.listener.OnLoadCompleteListener()
    {
        @Override
        public void loadComplete(int i) {
            updateView(CurrentView.READ_LAYOUT);
        }
    };

    //handler del pageChanged del PDF
    private com.joanzapata.pdfview.listener.OnPageChangeListener pdfPageChangedListener = new com.joanzapata.pdfview.listener.OnPageChangeListener()
    {
        @Override
        public void onPageChanged(int i, int i1) {
            currentPage = pdfViewer.getCurrentPage()+1;
            updateActionBarText();
        }
    };

    //funcion handler del click en devicelist
    private AdapterView.OnItemClickListener myListClickListener = new AdapterView.OnItemClickListener()
    {
        public void onItemClick (AdapterView av, View v, int arg2, long arg3)
        {
            // Get the device MAC address, the last 17 chars in the View
            String info = ((TextView) v).getText().toString();
            address = info.substring(info.length() - 17);
            //conectamos
            ConnectBT conectar = new ConnectBT();
            conectar.execute();
        }
    };


    //tarea asincrona para cargar la lista de archivos PDF
    private class PdfListLoadTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... iniPath) {
            //File files = new File("/sdcard/PDFDemo_AndroidSRC/");
            //File files = new File(Environment.getExternalStorageDirectory().getPath()+"/PDFDemo_AndroidSRC/");
            currentPath = iniPath[0].toString();
            filePathStack.push(currentPath);

            File files = new File(iniPath[0].toString());

            String[] lista = files.list();

            //filtramos los archivos que son pdf o son directorios
            filelist = files.listFiles(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return ((name.endsWith(".pdf")) || !name.contains("."));
                   }
            });

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            // TODO Auto-generated method stub

            if (filelist != null && filelist.length >= 1) {
                ArrayList<String> fileNameList = new ArrayList<String>();
                filePathList = new ArrayList();
                fileTypeList = new ArrayList();

                for (int i = 0; i < filelist.length; i++) {
                    fileNameList.add(filelist[i].getName());
                    filePathList.add(filelist[i].getPath());
                    if (filelist[i].isDirectory())
                        fileTypeList.add(0);
                    else
                        fileTypeList.add(1);
                }

                adapter = new ArrayAdapter<String>(getApplicationContext(),
                        R.layout.list_item, fileNameList);
                pdfList.setAdapter(adapter);
            } else {
                pdfList.setAdapter(null);
            }

            updateActionBarText();
        }

    }

    //funcion para obtener la lsita de dispositivos BT emparejados
    private void pairedDevicesList()
    {
        Set <BluetoothDevice> pairedDevices;
        pairedDevices = myBluetooth.getBondedDevices();
        ArrayList list = new ArrayList();

        if (pairedDevices.size()>0)
        {
            for(BluetoothDevice bt : pairedDevices)
            {
                list.add(bt.getName() + "\n" + bt.getAddress()); //Get the device's name and the address
            }
        }
        else
        {
            Toast.makeText(getApplicationContext(), "No se encuentran dispositivos emparejados.", Toast.LENGTH_LONG).show();
        }

        final ArrayAdapter adapter = new ArrayAdapter(this,android.R.layout.simple_list_item_1, list);
        devicelist.setAdapter(adapter);
        devicelist.setOnItemClickListener(myListClickListener); //Method called when the device from the list is clicked

    }

    //tarea asincrona para conectar al dispositov BT
    private class ConnectBT extends AsyncTask<Void, Void, Void>  // UI thread
    {
        private boolean ConnectSuccess = true;

        @Override
        protected void onPreExecute()
        {
            progress = ProgressDialog.show(AppActivity.this, "Conectando...", "Porfavor espere!!!");  //show a progress dialog
        }

        @Override
        protected Void doInBackground(Void... devices) //while the progress dialog is shown, the connection is done in background
        {
            try
            {
                if (btSocket == null || !isBtConnected)
                {
                    myBluetooth = BluetoothAdapter.getDefaultAdapter();//get the mobile bluetooth device
                    BluetoothDevice dispositivo = myBluetooth.getRemoteDevice(address);//connects to the device's address and checks if it's available
                    btSocket = dispositivo.createInsecureRfcommSocketToServiceRecord(myUUID);//create a RFCOMM (SPP) connection
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                    btSocket.connect();//start connection
                }
            }
            catch (IOException e)
            {
                Log.d("PDFSCORE",e.toString());
                ConnectSuccess = false;//if the try failed, you can check the exception here
            }
            return null;
        }
        @Override
        protected void onPostExecute(Void result) //after the doInBackground, it checks if everything went fine
        {
            super.onPostExecute(result);

            if (!ConnectSuccess)
            {
                msg("Conexión fallida");
            }
            else
            {
                msg("Conectado");
                isBtConnected = true;
                //enviamos algo
                if (btSocket!=null)
                {
                    try
                    {
                        btSocket.getOutputStream().write("PSOK".toString().getBytes());
                    }
                    catch (IOException e)
                    {
                        msg("Error");
                    }
                }
                //lanzamos thread de conexion
                mConnectedThread = new ConnectedThread(btSocket);
                mConnectedThread.start();

                if (openedPDF)
                    updateView(CurrentView.READ_LAYOUT);
                else
                    updateView(CurrentView.PDF_SELECTION_LAYOUT);

            }
            progress.dismiss();
        }

        private void msg(String s)
        {
            Toast.makeText(getApplicationContext(),s,Toast.LENGTH_LONG).show();
        }
    }

    //thread de conexion
    private class ConnectedThread extends Thread {
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        volatile boolean running = true;

        public ConnectedThread(BluetoothSocket socket) {
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[256];  // buffer store for the stream
            int bytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs
            while (running) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);        // Get number of bytes and message in "buffer"
                    String readMessage = new String(buffer, 0, bytes);
                    h.obtainMessage(RECEIVE_MSG, bytes, -1, readMessage).sendToTarget();     // Send to message queue Handler
                } catch (IOException e) {
                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(String message) {
            //Log.d(TAG, "...Data to send: " + message + "...");
            byte[] msgBuffer = message.getBytes();
            try {
                mmOutStream.write(msgBuffer);
            } catch (IOException e) {
                //Log.d(TAG, "...Error data send: " + e.getMessage() + "...");
            }
        }
    }

    //funcion para cerrar la aplicacion
    private void exitApp(){
        //cerramos BlueTooth
        if (isBtConnected) {
            try {
                mConnectedThread.write(DISCONNECT_ST);
                btSocket.close();
                isBtConnected = false;
                mConnectedThread.running = false;
                myBluetooth = null;
            } catch (IOException ex) {
                Log.d("DEBUG", ex.toString());
            }
        }
        else
            Log.d("PDFSCORE","No estaba conectado");

        finish();
    }

}
