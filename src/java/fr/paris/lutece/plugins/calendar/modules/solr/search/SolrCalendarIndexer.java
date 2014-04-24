/*
 * Copyright (c) 2002-2014, Mairie de Paris
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
import java.util.Iterator;
import java.util.List;

import org.apache.lucene.demo.html.HTMLParser;

import fr.paris.lutece.plugins.calendar.business.Agenda;
import fr.paris.lutece.plugins.calendar.business.CalendarHome;
import fr.paris.lutece.plugins.calendar.business.Event;
import fr.paris.lutece.plugins.calendar.business.OccurrenceEvent;
import fr.paris.lutece.plugins.calendar.business.SimpleEvent;
import fr.paris.lutece.plugins.calendar.business.category.Category;
import fr.paris.lutece.plugins.calendar.service.AgendaResource;
import fr.paris.lutece.plugins.calendar.service.CalendarPlugin;
import fr.paris.lutece.plugins.calendar.service.Utils;
import fr.paris.lutece.plugins.calendar.utils.CalendarIndexerUtils;
import fr.paris.lutece.plugins.calendar.web.Constants;
import fr.paris.lutece.plugins.search.solr.business.field.Field;
import fr.paris.lutece.plugins.search.solr.indexer.SolrIndexer;
import fr.paris.lutece.plugins.search.solr.indexer.SolrIndexerService;
import fr.paris.lutece.plugins.search.solr.indexer.SolrItem;
import fr.paris.lutece.plugins.search.solr.util.SolrConstants;
import fr.paris.lutece.portal.service.content.XPageAppService;
import fr.paris.lutece.portal.service.plugin.Plugin;
import fr.paris.lutece.portal.service.plugin.PluginService;
import fr.paris.lutece.portal.service.util.AppLogService;
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
    private static final String PROPERTY_CALENDAR_ID_LABEL = "calendar-solr.indexer.calendar_id.label";
    private static final String PROPERTY_CALENDAR_ID_DESCRIPTION = "calendar-solr.indexer.calendar_id.description";
    private static final List<String> LIST_RESSOURCES_NAME = new ArrayList<String>(  );

    private static final String EVENT_INDEXATION_ERROR = "[SolrCalendarIndexer] An error occured during the indexation of the event number ";
    
    public SolrCalendarIndexer(  )
    {
        super(  );

        LIST_RESSOURCES_NAME.add( CalendarIndexerUtils.CONSTANT_TYPE_RESOURCE );
    }

    /**
     * {@inheritDoc}
     */
    public String getDescription(  )
    {
        return AppPropertiesService.getProperty( PROPERTY_DESCRIPTION );
    }

    /**
     * {@inheritDoc}
     */
    public String getName(  )
    {
        return AppPropertiesService.getProperty( PROPERTY_NAME );
    }

    /**
     * {@inheritDoc}
     */
    public String getVersion(  )
    {
        return AppPropertiesService.getProperty( PROPERTY_VERSION );
    }

    /**
     * {@inheritDoc}
     */
    public List<String> indexDocuments(  )
    {
        String sRoleKey = "";

        List<String> lstErrors = new ArrayList<String>(  );
        
        for ( AgendaResource agenda : Utils.getAgendaResourcesWithOccurrences(  ) )
        {
            sRoleKey = agenda.getRole(  );

            String strAgenda = agenda.getId(  );

            for ( Object oEvent : agenda.getAgenda(  ).getEvents(  ) )
            {
            	try
				{
            		indexSubject( oEvent, sRoleKey, strAgenda );
				}
				catch ( Exception e )
				{
					lstErrors.add( SolrIndexerService.buildErrorMessage( e ) );
					AppLogService.error( EVENT_INDEXATION_ERROR + ( (Event) oEvent ).getId(  ), e );
				}
            }
        }
        
        return lstErrors;
    }

    /**
     * Get the calendar document
     * @param strDocument id of the subject to index
     * @return The list of Solr items
     * @throws IOException the exception
     */
    public List<SolrItem> getDocuments( String strDocument )
    {
        List<SolrItem> listDocs = new ArrayList<SolrItem>(  );
        Plugin plugin = PluginService.getPlugin( CalendarPlugin.PLUGIN_NAME );

        OccurrenceEvent occurrence = CalendarHome.findOccurrence( Integer.parseInt( strDocument ), plugin );

        if ( !occurrence.getStatus(  )
                            .equals( AppPropertiesService.getProperty( Constants.PROPERTY_EVENT_STATUS_CONFIRMED ) ) )
        {
            return null;
        }

        SimpleEvent event = CalendarHome.findEvent( occurrence.getEventId(  ), plugin );

        AgendaResource agendaResource = CalendarHome.findAgendaResource( event.getIdCalendar(  ), plugin );
        Utils.loadAgendaOccurrences( agendaResource, plugin );

        String sRoleKey = agendaResource.getRole(  );
        Agenda agenda = agendaResource.getAgenda(  );

        UrlItem urlEvent = new UrlItem( SolrIndexerService.getBaseUrl(  ) );
        urlEvent.addParameter( XPageAppService.PARAM_XPAGE_APP, CalendarPlugin.PLUGIN_NAME );
        urlEvent.addParameter( Constants.PARAMETER_ACTION, Constants.ACTION_SHOW_RESULT );
        urlEvent.addParameter( Constants.PARAMETER_EVENT_ID, occurrence.getEventId(  ) );
        urlEvent.addParameter( Constants.PARAM_AGENDA, agenda.getKeyName(  ) );

        SolrItem docEvent;

        try
        {
            docEvent = getDocument( occurrence, sRoleKey, urlEvent.getUrl(  ), agenda.getKeyName(  ) );
            listDocs.add( docEvent );
        }
        catch ( IOException e )
        {
            throw new RuntimeException( e );
        }

        return listDocs;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isEnable(  )
    {
        return "true".equalsIgnoreCase( AppPropertiesService.getProperty( PROPERTY_INDEXER_ENABLE ) );
    }

    /**
     * {@inheritDoc}
     */
    public List<Field> getAdditionalFields(  )
    {
        List<Field> fields = new ArrayList<Field>(  );
        Field field = new Field(  );
        field.setEnableFacet( false );
        field.setName( Constants.FIELD_CALENDAR_ID );
        field.setLabel( AppPropertiesService.getProperty( PROPERTY_CALENDAR_ID_LABEL ) );
        field.setDescription( AppPropertiesService.getProperty( PROPERTY_CALENDAR_ID_DESCRIPTION ) );
        fields.add( field );

        return fields;
    }

    /**
     * Recursive method for indexing a calendar event
     *
     * @param faq the faq linked to the subject
     * @param subject the subject
     * @throws IOException I/O Exception
     */
    private void indexSubject( Object oEvent, String sRoleKey, String strAgenda )
        throws IOException
    {
        OccurrenceEvent occurrence = (OccurrenceEvent) oEvent;

        if ( occurrence.getStatus(  )
                           .equals( AppPropertiesService.getProperty( Constants.PROPERTY_EVENT_STATUS_CONFIRMED ) ) )
        {
            UrlItem urlEvent = new UrlItem( SolrIndexerService.getBaseUrl(  ) );
            urlEvent.addParameter( XPageAppService.PARAM_XPAGE_APP, CalendarPlugin.PLUGIN_NAME );
            urlEvent.addParameter( Constants.PARAMETER_ACTION, Constants.ACTION_SHOW_RESULT );
            urlEvent.addParameter( Constants.PARAMETER_EVENT_ID, occurrence.getEventId(  ) );
            urlEvent.addParameter( Constants.PARAM_AGENDA, strAgenda );

            SolrItem docSubject = getDocument( occurrence, sRoleKey, urlEvent.getUrl(  ), strAgenda );

            SolrIndexerService.write( docSubject );
        }
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
        item.addDynamicField( Constants.FIELD_CALENDAR_ID, strAgenda + "_" + Constants.CALENDAR_SHORT_NAME );

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
        item.setUid( getResourceUid( strIdEvent, CalendarIndexerUtils.CONSTANT_TYPE_RESOURCE ) );

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
        item.setSite( SolrIndexerService.getWebAppName(  ) );

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

    /**
         * {@inheritDoc}
         */
    public List<String> getResourcesName(  )
    {
        return LIST_RESSOURCES_NAME;
    }

    /**
     * {@inheritDoc}
     */
    public String getResourceUid( String strResourceId, String strResourceType )
    {
        StringBuffer sb = new StringBuffer( strResourceId );
        sb.append( SolrConstants.CONSTANT_UNDERSCORE ).append( Constants.CALENDAR_SHORT_NAME );

        return sb.toString(  );
    }
}
