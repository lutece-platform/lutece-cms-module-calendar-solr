/*
 * Copyright (c) 2002-2008, Mairie de Paris
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice
 *     and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright notice
 *     and the following disclaimer in the documentation and/or other materials
 *     provided with the distribution.
 *
 *  3. Neither the name of 'Mairie de Paris' nor 'Lutece' nor the names of its
 *     contributors may be used to endorse or promote products derived from
 *     this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 * License 1.0
 */
package fr.paris.lutece.plugins.calendar.modules.solr.search;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.lucene.demo.html.HTMLParser;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import fr.paris.lutece.plugins.calendar.business.Event;
import fr.paris.lutece.plugins.calendar.service.AgendaResource;
import fr.paris.lutece.plugins.calendar.service.Utils;
import fr.paris.lutece.plugins.calendar.web.Constants;
import fr.paris.lutece.plugins.search.solr.business.SolrServerService;
import fr.paris.lutece.plugins.search.solr.indexer.SolrIndexer;
import fr.paris.lutece.plugins.search.solr.indexer.SolrItem;
import fr.paris.lutece.portal.service.util.AppLogService;
import fr.paris.lutece.portal.service.util.AppPathService;
import fr.paris.lutece.portal.service.util.AppPropertiesService;
import fr.paris.lutece.util.url.UrlItem;


/**
 * The Calendar indexer for Solr search platform
 *
 */
public class SolrCalendarIndexer implements SolrIndexer
{
    private static final String PROPERTY_DESCRIPTION = "calendar-solr.indexer.description";
    private static final String PROPERTY_NAME = "calendar-solr.indexer.name";
    private static final String PROPERTY_VERSION = "calendar-solr.indexer.version";
    private static final String PROPERTY_INDEXER_ENABLE = "calendar-solr.indexer.enable";
    private static final String SITE = AppPropertiesService.getProperty( "lutece.name" );
    private static final SolrServer SOLR_SERVER = SolrServerService.getInstance(  ).getSolrServer(  );
    private static final String BLANK = " ";
    private static final String TYPE = "CALENDAR";

    public String getDescription(  )
    {
        return AppPropertiesService.getProperty( PROPERTY_DESCRIPTION );
    }

    public String getName(  )
    {
        return AppPropertiesService.getProperty( PROPERTY_NAME );
    }

    public String getVersion(  )
    {
        return AppPropertiesService.getProperty( PROPERTY_VERSION );
    }

    public String index(  )
    {
        StringBuilder sbLogs = new StringBuilder(  );

        //Page page;
        for ( AgendaResource agenda : Utils.getAgendaResourcesWithOccurrences(  ) )
        {
            try
            {
                sbLogs.append( "indexing " );
                sbLogs.append( TYPE );
                sbLogs.append( " id : " );
                sbLogs.append( agenda.getId(  ) );
                sbLogs.append( " Name : " );
                sbLogs.append( agenda.getName(  ) );
                sbLogs.append( "<br/>" );

                Collection<SolrItem> items = new ArrayList<SolrItem>(  );

                getItems( agenda );

                SOLR_SERVER.addBeans( items );

                SOLR_SERVER.commit(  );
            }
            catch ( IOException e )
            {
                AppLogService.error( e );
            }
            catch ( SolrServerException e )
            {
                AppLogService.error( e );
            }
        }

        return sbLogs.toString(  );
    }

    /**
     * Builds a {@link SolrItem} which will be used by Solr during the indexing of the agenda and his events
     * @return the {@link SolrItem} collection
     * @param agenda The {@link AgendaResource} to index
     * @throws IOException The IO Exception
     */
    private Collection<SolrItem> getItems( AgendaResource agenda )
        throws IOException
    {
        // the item
        Collection<SolrItem> items = new ArrayList<SolrItem>(  );
        SolrItem item = new SolrItem(  );

        // Setting the URL field
        UrlItem url = new UrlItem( AppPathService.getPortalUrl(  ) ); //FIXME relative path OK ?
        url.addParameter( Constants.PARAMETER_PAGE, Constants.PLUGIN_NAME );
        url.addParameter( Constants.PARAMETER_CALENDAR_ID, agenda.getId(  ) );
        item.setUrl( url.getUrl(  ) );

        // Setting the Date field
        //No creation/modification date available in agenda

        // Setting the Uid field
        item.setUid( agenda.getId(  ) );

        // Setting the Title field
        item.setTitle( agenda.getName(  ) );

        // Setting the Site field
        item.setSite( SITE );

        // Setting the Summary field
        //No summary available in agenda

        // Setting the Type field
        item.setType( TYPE );

        // Setting the XmlContent field
        //No XmlContent available in agenda

        // Setting the Categorie field
        //No Categorie available in agenda

        // Setting the HieDate field
        //No HieDate available in agenda

        // Setting the Role field
        item.setRole( agenda.getRole(  ) );

        //Setting the Content field
        //No Content available in agenda, get the agenda name
        item.setContent( agenda.getName(  ) );

        items.add( item );

        //Add SolrItem for each events 
        for ( Event oEvent : (List<Event>) agenda.getAgenda(  ).getEvents(  ) )
        {
            items.add( getItem( agenda, oEvent ) );
        }

        return items;
    }

    /**
     * Builds a {@link SolrItem} which will be used by Solr during the indexing of the agenda events
     * @return the {@link SolrItem} corresponding to the indexed event
     * @param agenda The {@link AgendaResource}
     * @param event The event to index
     * @throws IOException The IO Exception
     */
    private SolrItem getItem( AgendaResource agenda, Event event )
        throws IOException
    {
        // the item
        SolrItem item = new SolrItem(  );

        // Setting the URL field
        UrlItem url = new UrlItem( AppPathService.getPortalUrl(  ) ); //FIXME relative path OK ?
        url.addParameter( Constants.PARAMETER_PAGE, Constants.PLUGIN_NAME );
        url.addParameter( Constants.PARAMETER_ACTION, Constants.ACTION_SHOW_RESULT );
        url.addParameter( Constants.PARAMETER_CALENDAR_ID, event.getIdCalendar(  ) );
        url.addParameter( Constants.PARAMETER_EVENT_ID, event.getId(  ) );
        item.setUrl( url.getUrl(  ) );

        // Setting the Date field
        //No creation/modification date available in agenda

        // Setting the Uid field
        item.setUid( Integer.toString( event.getId(  ) ) );

        // Setting the Title field
        item.setTitle( event.getTitle(  ) );

        // Setting the Site field
        item.setSite( SITE );

        // Setting the Summary field
        //No summary available in agenda

        // Setting the Type field
        item.setType( TYPE );

        // Setting the XmlContent field
        //No XmlContent available in agenda

        // Setting the Categorie field
        //No Categorie available in agenda

        // Setting the HieDate field
        //No HieDate available in agenda

        // Setting the Role field
        item.setRole( agenda.getRole(  ) );

        //Setting the Content field
        String strContentToIndex = "";
        strContentToIndex += ( event.getDescription(  ) + BLANK );
        strContentToIndex += ( event.getLocationAddress(  ) + BLANK );
        strContentToIndex += ( event.getLocationTown(  ) + BLANK );
        strContentToIndex += ( event.getLocationZip(  ) + BLANK );

        StringReader readerPage = new StringReader( strContentToIndex );
        HTMLParser parser = new HTMLParser( readerPage );

        Reader reader = parser.getReader(  );
        int c;
        StringBuffer sb = new StringBuffer(  );

        while ( ( c = reader.read(  ) ) != -1 )
        {
            sb.append( String.valueOf( (char) c ) );
        }

        reader.close(  );
        item.setContent( sb.toString(  ) );

        return item;
    }

    public boolean isEnable(  )
    {
        return "true".equalsIgnoreCase( AppPropertiesService.getProperty( PROPERTY_INDEXER_ENABLE ) );
    }
}
