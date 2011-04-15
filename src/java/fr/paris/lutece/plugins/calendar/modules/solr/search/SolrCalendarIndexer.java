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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.lucene.demo.html.HTMLParser;
import fr.paris.lutece.plugins.calendar.business.Event;
import fr.paris.lutece.plugins.calendar.business.OccurrenceEvent;
import fr.paris.lutece.plugins.calendar.business.category.Category;
import fr.paris.lutece.plugins.calendar.service.AgendaResource;
import fr.paris.lutece.plugins.calendar.service.CalendarPlugin;
import fr.paris.lutece.plugins.calendar.service.Utils;
import fr.paris.lutece.plugins.calendar.web.Constants;
import fr.paris.lutece.plugins.search.solr.business.field.Field;
import fr.paris.lutece.plugins.search.solr.indexer.SolrIndexer;
import fr.paris.lutece.plugins.search.solr.indexer.SolrItem;
import fr.paris.lutece.portal.service.content.XPageAppService;
import fr.paris.lutece.portal.service.plugin.Plugin;
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
    private static final String BLANK = " ";
    private static final String PROPERTY_DESCRIPTION_MAX_CHARACTERS = "calendar-solr.description.max.characters";
    private static final String PROPERTY_DESCRIPTION_ETC = "...";

    // Site name
    private static final String PROPERTY_SITE = "lutece.name";
    private static final String PROPERTY_PROD_URL = "lutece.prod.url";
    private String _strSite;
    private String _strProdUrl;

    public SolrCalendarIndexer(  )
    {
        super(  );
        _strSite = AppPropertiesService.getProperty( PROPERTY_SITE );
        _strProdUrl = AppPropertiesService.getProperty( PROPERTY_PROD_URL );

        if ( !_strProdUrl.endsWith( "/" ) )
        {
            _strProdUrl = _strProdUrl + "/";
        }
    }

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

    public Map<String, SolrItem> index(  )
    {
        Map<String, SolrItem> items = new HashMap<String, SolrItem>(  );
        String sRoleKey = "";

        for ( AgendaResource agenda : Utils.getAgendaResourcesWithOccurrences(  ) )
        {
            sRoleKey = agenda.getRole(  );

            String strAgenda = agenda.getId(  );

            for ( Object oEvent : agenda.getAgenda(  ).getEvents(  ) )
            {
                try
                {
                    items.putAll( indexSubject( oEvent, sRoleKey, strAgenda ) );
                }
                catch ( IOException e )
                {
                    AppLogService.error( e );
                }
            }
        }

        return items;
    }

    /**
     * Recursive method for indexing a calendar event
     *
     * @param faq the faq linked to the subject
     * @param subject the subject
     * @throws IOException I/O Exception
     */
    private Map<String, SolrItem> indexSubject( Object oEvent, String sRoleKey, String strAgenda )
        throws IOException
    {
        Map<String, SolrItem> items = new HashMap<String, SolrItem>(  );
        OccurrenceEvent occurrence = (OccurrenceEvent) oEvent;

        if ( occurrence.getStatus(  )
                           .equals( AppPropertiesService.getProperty( Constants.PROPERTY_EVENT_STATUS_CONFIRMED ) ) )
        {
            String strPortalUrl = AppPathService.getPortalUrl(  );

            UrlItem urlEvent = new UrlItem( _strProdUrl + strPortalUrl );
            urlEvent.addParameter( XPageAppService.PARAM_XPAGE_APP, CalendarPlugin.PLUGIN_NAME );
            urlEvent.addParameter( Constants.PARAMETER_ACTION, Constants.ACTION_SHOW_RESULT );
            urlEvent.addParameter( Constants.PARAMETER_EVENT_ID, occurrence.getEventId(  ) );
            urlEvent.addParameter( Constants.PARAM_AGENDA, strAgenda );

            SolrItem docSubject = getDocument( occurrence, sRoleKey, urlEvent.getUrl(  ), strAgenda );

            items.put( getLog( docSubject ), docSubject );
        }

        return items;
    }

    /**
     * Builds a {@link SolrItem} which will be used by solr during the indexing of the calendar list
     *
     * @param nIdFaq The {@link Faq} Id
     * @param strUrl the url of the subject
     * @param strRoleKey The role key
     * @param plugin The {@link Plugin}
     * @param strAgenda the calendar id
     * @return A Solr item containing QuestionAnswer Data
     * @throws IOException The IO Exception
     */
    private SolrItem getDocument( OccurrenceEvent occurrence, String strRoleKey, String strUrl, String strAgenda )
        throws IOException
    {
        // make a new, empty document
        SolrItem item = new SolrItem(  );

        //add the id of the calendar
        //        doc.add( new Field( Constants.FIELD_CALENDAR_ID, strAgenda + "_" + Constants.CALENDAR_SHORT_NAME,
        //                Field.Store.NO, Field.Index.NOT_ANALYZED ) );

        //add the category of the event
        Collection<Category> arrayCategories = occurrence.getListCategories(  );
        List<String> listCategories = new ArrayList<String>(  );

        if ( arrayCategories != null )
        {
            Iterator<Category> i = arrayCategories.iterator(  );

            while ( i.hasNext(  ) )
                listCategories.add( i.next(  ).getName(  ) );
        }

        // Setting the Categorie field
        item.setCategorie( listCategories );

        // Setting the Role field
        item.setRole( strRoleKey );

        // Setting the Url field
        item.setUrl( strUrl );

        // Setting the Uid field
        String strIdEvent = String.valueOf( occurrence.getId(  ) );
        item.setUid( strIdEvent + "_" + Constants.CALENDAR_SHORT_NAME );

        // Setting the date field
        item.setDate( occurrence.getDate(  ) );

        // Setting the content field
        String strContentToIndex = getContentToIndex( occurrence );
        StringReader readerPage = new StringReader( strContentToIndex );
        HTMLParser parser = new HTMLParser( readerPage );

        //the content of the event descriptionr is recovered in the parser because this one
        //had replaced the encoded caracters (as &eacute;) by the corresponding special caracter (as ?)
        Reader reader = parser.getReader(  );
        int c;
        StringBuffer sb = new StringBuffer(  );

        while ( ( c = reader.read(  ) ) != -1 )
        {
            sb.append( String.valueOf( (char) c ) );
        }

        reader.close(  );
        item.setContent( sb.toString(  ) );

        // Setting the summary field
        // Add the description as a summary field, so that index can be incrementally maintained.
        // This field is stored, but it is not indexed
        String strDescription = occurrence.getDescription(  );
        strDescription = Utils.ParseHtmlToPlainTextString( strDescription );

        try
        {
            strDescription = strDescription.substring( 0,
                    AppPropertiesService.getPropertyInt( PROPERTY_DESCRIPTION_MAX_CHARACTERS, 200 ) ) +
                PROPERTY_DESCRIPTION_ETC;
        }
        catch ( StringIndexOutOfBoundsException e )
        {
        }
        catch ( NullPointerException e )
        {
        }

        item.setSummary( strDescription );

        // Setting the title field
        item.setTitle( occurrence.getTitle(  ) );

        // Setting the Site field
        item.setSite( _strSite );

        // Setting the type field
        item.setType( CalendarPlugin.PLUGIN_NAME );

        // return the item
        return item;
    }

    /**
     * Set the Content to index (Description, location)
     * @param  The Event
     * @return The content to index
     */
    private String getContentToIndex( Event event )
    {
        StringBuffer sbContentToIndex = new StringBuffer(  );
        //Do not index question here
        sbContentToIndex.append( event.getDescription(  ) );
        sbContentToIndex.append( BLANK );
        sbContentToIndex.append( event.getLocationAddress(  ) );
        sbContentToIndex.append( BLANK );
        sbContentToIndex.append( event.getLocationTown(  ) );
        sbContentToIndex.append( BLANK );
        sbContentToIndex.append( event.getLocationZip(  ) );

        return sbContentToIndex.toString(  );
    }

    public boolean isEnable(  )
    {
        return "true".equalsIgnoreCase( AppPropertiesService.getProperty( PROPERTY_INDEXER_ENABLE ) );
    }

    public List<Field> getAdditionalFields(  )
    {
        return null;
    }

    /**
     * Generate the log line for the specified {@link SolrItem}
     * @param item The {@link SolrItem}
     * @return The string representing the log line
     */
    private String getLog( SolrItem item )
    {
        StringBuilder sbLogs = new StringBuilder(  );
        sbLogs.append( "indexing " );
        sbLogs.append( item.getType(  ) );
        sbLogs.append( " id : " );
        sbLogs.append( item.getUid(  ) );
        sbLogs.append( " Title : " );
        sbLogs.append( item.getTitle(  ) );
        sbLogs.append( "<br/>" );

        return sbLogs.toString(  );
    }
}
