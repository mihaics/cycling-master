package luka.cyclingmaster;

import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import adapters.RoutesAdapter;
import dialogs.ImportGpxDialogFragment;
import gpslogger.CyclingRoute;
import gpslogger.GpxWriter;
import utils.DateUtilities;
import utils.FileComparator;
import utils.FileUtilities;
import utils.GpxParser;
import utils.Utils;


public class ListRoutesActivity extends ActionBarActivity implements ImportGpxDialogFragment.NoticeDialogListener {

    private RecyclerView recyclerViewListRoutes;
    private RecyclerView.LayoutManager recyclerViewLinerLayoutManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list_routes);

        recyclerViewListRoutes = (RecyclerView) findViewById(R.id.recyclerViewListRoutes);
        recyclerViewListRoutes.setHasFixedSize(true);

        recyclerViewLinerLayoutManager = new LinearLayoutManager(this);
        recyclerViewListRoutes.setLayoutManager(recyclerViewLinerLayoutManager);

        loadRoutes();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_list_routes, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_importGpx) {
            FragmentManager fm = getSupportFragmentManager();
            ImportGpxDialogFragment importGpxDialog = new ImportGpxDialogFragment();
            importGpxDialog.show(fm, "dialog_import_gpx");
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private CyclingRoute[] getSavedRoutes() {
        ArrayList<CyclingRoute> listRoutes = new ArrayList<CyclingRoute>();

        File dirExternalStorageRoot = getExternalFilesDir(null);
        File dirExternalStorageGpxStore = new File(dirExternalStorageRoot.getAbsolutePath() + "/gpx/");
        dirExternalStorageGpxStore.mkdirs();
        String extension;

        File[] dayDirArray = dirExternalStorageGpxStore.listFiles();

        if(dayDirArray != null)
        {
            Arrays.sort(dayDirArray, Collections.reverseOrder(new FileComparator()));

            for(File dayDir : dayDirArray) {
                File[] routesArray = dayDir.listFiles();

                if(routesArray != null)
                {
                    int i = 0;

                    for(File fileRoute : routesArray) {
                        extension = Utils.getFileExtension(fileRoute.getName());

                        if(extension.equals("dat")) {
                            // Preberemo podatke iz datoteke
                            try {
                                BufferedReader reader = new BufferedReader(new FileReader(fileRoute));
                                String line = null;
                                String[] podatki;
                                Date date;
                                CyclingRoute route = new CyclingRoute();

                                while ((line = reader.readLine()) != null) {
                                    podatki = line.split(": ");

                                    if(podatki.length == 2) {
                                        if(podatki[0].equals("Name")) {
                                            route = new CyclingRoute();
                                            route.setName(podatki[1].trim());
                                            route.setFirst(false);
                                            if(i == 0)
                                                route.setFirst(true);
                                        } else if(podatki[0].equals("Distance")) {
                                            route.setDistance(Double.parseDouble(podatki[1].trim().replace(',', '.')));
                                        } else if(podatki[0].equals("Time")) {
                                            route.setTime(DateUtilities.timeStringToMillis(podatki[1].trim()));
                                        } else if(podatki[0].equals("Start time")) {
                                            date = DateUtilities.getDate(podatki[1].trim());
                                            if(date != null)
                                                route.setStartTime(date);
                                        }
                                        else if(podatki[0].equals("End time")) {
                                            date = DateUtilities.getDate(podatki[1].trim());
                                            if(date != null)
                                                route.setEndTime(date);
                                        }
                                        else if(podatki[0].equals("Max speed")) {
                                            route.setMaxSpeed(Double.parseDouble(podatki[1].trim().replace(',', '.')));
                                        }
                                        else if(podatki[0].equals("Average speed")) {
                                            route.setAverageSpeed(Double.parseDouble(podatki[1].trim().replace(',', '.')));
                                        }
                                        else if(podatki[0].equals("Altitude")) {
                                            route.setAltitude(Double.parseDouble(podatki[1].trim()));
                                        }
                                    }
                                }

                                listRoutes.add(route);
                                i++;
                                reader.close();
                            } catch(Exception ex) {
                                Toast.makeText(this, this.getResources().getString(R.string.error_settings_params), Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                }
            }
        }

        return listRoutes.toArray(new CyclingRoute[listRoutes.size()]);
    }

    @Override
    public void onDialogImportGpxPositiveClick(DialogFragment dialog, String fileName, String currentFileName, String currentFilePath) {
        uploadGpxToDatabase(currentFilePath + "/" + currentFileName);
        loadRoutes();
    }

    private void uploadGpxToDatabase(String gpxFilePath) {
        if(gpxFilePath.equals("null/null"))
        {
            Toast.makeText(getApplicationContext(), getResources().getString(R.string.no_gpx_file_chosen), Toast.LENGTH_LONG).show();
            return;
        }
        else if(!gpxFilePath.endsWith(".gpx"))
        {
            Toast.makeText(getApplicationContext(), getResources().getString(R.string.gpx_file_check_error), Toast.LENGTH_LONG).show();
            return;
        }

        File gpxFile = new File(gpxFilePath);
        String fileName = gpxFile.getName();
        GpxParser gpxParser = new GpxParser(gpxFilePath);
        CyclingRoute route = gpxParser.getCyclingRoute( gpxParser.getArrayLocations() );

        if(gpxParser.LAST_ERROR != null)
        {
            Toast.makeText(getApplicationContext(), gpxParser.LAST_ERROR, Toast.LENGTH_LONG).show();
            return;
        }

        File dirExternalStorageRoot = getExternalFilesDir(null);
        File dirExternalStorageGpxStore = new File(dirExternalStorageRoot.getAbsolutePath(), "gpx");
        DateFormat df = new SimpleDateFormat("dd.MM.yyyy");
        File dirTodayGpxStore = new File(dirExternalStorageGpxStore, df.format(route.getStartTime()));
        dirTodayGpxStore.mkdirs();
        File savedGpxData = new File(dirTodayGpxStore, fileName.replace(".gpx", ".dat"));

        GpxWriter.saveCyclingRouteData(savedGpxData, route);
        FileUtilities.copyFile(gpxFile, new File(dirTodayGpxStore, fileName), false);

        if(GpxWriter.GPX_DATA_ERROR != null)
            Toast.makeText(getApplicationContext(), GpxWriter.GPX_DATA_ERROR, Toast.LENGTH_LONG).show();
        else
            Toast.makeText(getApplicationContext(), getResources().getString(R.string.file_uploaded), Toast.LENGTH_LONG).show();

    }

    @Override
    public void onDialogImportGpxNegativeClick(DialogFragment dialog) {
        dialog.dismiss();
    }

    private void loadRoutes() {
        CyclingRoute[] routes = getSavedRoutes();
        RoutesAdapter routesAdapter = new RoutesAdapter(this, R.layout.row_route, routes);
        recyclerViewListRoutes.setAdapter(routesAdapter);
    }
}